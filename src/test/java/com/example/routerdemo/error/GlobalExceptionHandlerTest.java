package com.example.routerdemo.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.example.routerdemo.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Paths through the exception handler the real endpoints cannot trigger, driven by a test-only controller and filter. */
@ExtendWith(OutputCaptureExtension.class)
@Import({GlobalExceptionHandlerTest.Config.class, GlobalExceptionHandlerTest.FailingController.class})
class GlobalExceptionHandlerTest extends IntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandlerTest.class);

    @Autowired
    RecordingReporter reporter;

    record Report(Throwable error, ErrorContext context) {}

    /** Stands in for Sentry: records what it is given, or fails on demand. */
    static class RecordingReporter implements ErrorReporter {
        final List<Report> reports = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public void report(Throwable error, ErrorContext context) {
            if (failing) {
                throw new IllegalStateException("error tracker is down");
            }
            reports.add(new Report(error, context));
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        RecordingReporter recordingReporter() {
            return new RecordingReporter();
        }

        /** Stands in for a filter that rejects requests, such as a rate limiter. */
        @Bean
        WebFilter throwingFilter() {
            return (exchange, chain) -> {
                String mode = exchange.getRequest().getHeaders().getFirst("X-Test-Filter");
                if ("conflict".equals(mode)) {
                    exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "5");
                    throw new ConflictException("error.conflict", List.of(), "Request still in progress", null);
                }
                if ("crash".equals(mode)) {
                    throw new IllegalStateException("R2DBC connection refused: password=s3cr3t");
                }
                return chain.filter(exchange);
            };
        }
    }

    @RestController
    @RequestMapping("/test-errors")
    static class FailingController {

        @GetMapping("/crash")
        Mono<String> crash() {
            return Mono.error(new IllegalStateException("Connection refused: jdbc password=s3cr3t"));
        }

        @GetMapping("/timeout")
        Mono<String> timeout() {
            return Mono.<String>never().timeout(Duration.ofMillis(10));
        }

        @GetMapping("/cancelled")
        Mono<String> cancelled() {
            return Mono.error(new CancellationException("Job was cancelled"));
        }

        @GetMapping("/denied")
        Mono<String> denied() {
            return Mono.error(new AccessDeniedException("User 1 lacks ROLE_ADMIN"));
        }

        @GetMapping("/locked")
        Mono<String> locked() {
            return Mono.error(new OptimisticLockingFailureException("Failed to update table [posts] version 3"));
        }

        @GetMapping("/legacy")
        Mono<String> legacy() {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Needs ROLE_ADMIN in acme-corp"));
        }

        @GetMapping("/field")
        Mono<String> field() {
            return Mono.error(new FieldValidationException(
                    List.of(new FieldViolation("content", "error.validation.invalidValue")),
                    "Content matches a banned phrase"));
        }

        @GetMapping("/other-thread")
        Mono<String> otherThread() {
            return Mono.fromCallable(() -> {
                        log.info("Running on another thread");
                        return "ok";
                    })
                    .subscribeOn(Schedulers.boundedElastic());
        }
    }

    @BeforeEach
    void reset() {
        reporter.reports.clear();
        reporter.failing = false;
    }

    private WebTestClient.ResponseSpec get(String uri, String... headers) {
        return as(ALICE)
                .get()
                .uri(uri)
                .headers(h -> {
                    for (int i = 0; i < headers.length; i += 2) {
                        h.set(headers[i], headers[i + 1]);
                    }
                })
                .exchange();
    }

    private static WebTestClient.BodyContentSpec problem(WebTestClient.ResponseSpec response, int status, String code) {
        return response.expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(status)
                .jsonPath("$.code")
                .isEqualTo(code)
                .jsonPath("$.correlationId")
                .isNotEmpty();
    }

    private static void bodyExcludes(WebTestClient.BodyContentSpec body, String... fragments) {
        String text = new String(body.returnResult().getResponseBodyContent());
        for (String fragment : fragments) {
            assertThat(text).doesNotContain(fragment);
        }
    }

    @Test
    void unexpectedException_isAGeneric500LoggedWithTheCorrelationIdAndReported(CapturedOutput output) {
        var body = problem(get("/test-errors/crash?token=secret", "X-Correlation-ID", "req-123"), 500, "error.internal")
                .jsonPath("$.correlationId")
                .isEqualTo("req-123");
        bodyExcludes(body, "s3cr3t", "jdbc", "IllegalStateException");

        assertThat(output.getOut())
                .contains("correlationId=req-123")
                .contains("java.lang.IllegalStateException: Connection refused");
        assertThat(reporter.reports).hasSize(1);
        assertThat(reporter.reports.get(0).error()).isInstanceOf(IllegalStateException.class);
        assertThat(reporter.reports.get(0).context())
                .isEqualTo(new ErrorContext("req-123", "GET", "/test-errors/crash", 500, "error.internal"));
    }

    @Test
    void failingReporter_doesNotChangeTheResponse() {
        reporter.failing = true;
        problem(get("/test-errors/crash"), 500, "error.internal");
    }

    @Test
    void ownTimeout_is503LoggedAsAnErrorAndReported(CapturedOutput output) {
        get("/test-errors/timeout", "X-Correlation-ID", "req-timeout", HttpHeaders.ACCEPT_LANGUAGE, "fr")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("error.serviceUnavailable")
                .jsonPath("$.detail")
                .isEqualTo("Le service est temporairement indisponible. Veuillez réessayer plus tard.");

        String line = output.getOut()
                .lines()
                .filter(l -> l.contains("req-timeout") && l.contains("Request failed"))
                .findFirst()
                .orElseThrow();
        assertThat(line).contains(" ERROR ").contains("reason=timeout");
        assertThat(reporter.reports.get(0).context().status()).isEqualTo(503);
    }

    @Test
    void cancellation_is503LoggedAsAWarningNotReported(CapturedOutput output) {
        get("/test-errors/cancelled", "X-Correlation-ID", "req-cancel")
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("error.serviceUnavailable");

        List<String> lines =
                output.getOut().lines().filter(l -> l.contains("req-cancel")).toList();
        assertThat(lines.stream()
                        .filter(l -> l.contains("Request failed"))
                        .findFirst()
                        .orElseThrow())
                .contains(" WARN ")
                .contains("reason=cancelled");
        assertThat(lines).noneMatch(l -> l.contains(" ERROR "));
        assertThat(reporter.reports).isEmpty();
    }

    @Test
    void accessDenied_isATranslated403ThatHidesTheReason(CapturedOutput output) {
        var body = problem(get("/test-errors/denied", HttpHeaders.ACCEPT_LANGUAGE, "fr"), 403, "error.forbidden")
                .jsonPath("$.detail")
                .isEqualTo("Vous n’avez pas la permission d’effectuer cette action.");
        bodyExcludes(body, "ROLE_ADMIN");
        assertThat(output.getOut()).contains("User 1 lacks ROLE_ADMIN");
    }

    @Test
    void optimisticLockingFailure_isA409ThatHidesTableAndVersion() {
        bodyExcludes(problem(get("/test-errors/locked"), 409, "error.conflict"), "[posts]", "version 3");
    }

    @Test
    void responseStatusException_reasonIsNotSent() {
        bodyExcludes(problem(get("/test-errors/legacy"), 403, "error.forbidden"), "ROLE_ADMIN", "acme-corp");
    }

    @Test
    void manualFieldErrors_useTheSameFormatAsBeanValidation(CapturedOutput output) {
        var body = problem(get("/test-errors/field", HttpHeaders.ACCEPT_LANGUAGE, "fr"), 400, "error.validation")
                .jsonPath("$.errors.length()")
                .isEqualTo(1)
                .jsonPath("$.errors[0].field")
                .isEqualTo("content")
                .jsonPath("$.errors[0].code")
                .isEqualTo("error.validation.invalidValue")
                .jsonPath("$.errors[0].message")
                .isEqualTo("Valeur invalide.");
        bodyExcludes(body, "banned");
        assertThat(output.getOut())
                .contains("fields=[content:error.validation.invalidValue] reason=Content matches a banned phrase");
    }

    @Test
    void clientErrors_areNotReported() {
        get("/users/999999").expectStatus().isNotFound();
        get("/test-errors/denied").expectStatus().isForbidden();
        webTestClient.get().uri("/users/me").exchange().expectStatus().isUnauthorized();
        assertThat(reporter.reports).isEmpty();
    }

    @Test
    void apiExceptionInAFilter_getsTheTranslatedProblemAndKeepsFilterHeaders() {
        var response = get(
                        "/users/1",
                        "X-Test-Filter",
                        "conflict",
                        "X-Correlation-ID",
                        "req-filter",
                        HttpHeaders.ACCEPT_LANGUAGE,
                        "fr")
                .expectHeader()
                .valueEquals("X-Correlation-ID", "req-filter")
                .expectHeader()
                .valueEquals(HttpHeaders.RETRY_AFTER, "5");
        var body = problem(response, 409, "error.conflict")
                .jsonPath("$.detail")
                .isEqualTo("La requête est en conflit avec l’état actuel de la ressource.");
        bodyExcludes(body, "in progress");
        assertThat(reporter.reports).isEmpty();
    }

    @Test
    void unexpectedFailureInAFilter_isAGeneric500AndReported() {
        var body = problem(
                get("/users/1", "X-Test-Filter", "crash", "X-Correlation-ID", "req-filter-crash"),
                500,
                "error.internal");
        bodyExcludes(body, "s3cr3t", "connection refused");
        assertThat(reporter.reports).hasSize(1);
        assertThat(reporter.reports.get(0).context().correlationId()).isEqualTo("req-filter-crash");
    }

    @Test
    void unknownRoute_isATranslated404() {
        problem(get("/nope", HttpHeaders.ACCEPT_LANGUAGE, "fr"), 404, "error.notFound")
                .jsonPath("$.detail")
                .isEqualTo("La ressource demandée est introuvable.");
    }

    @Test
    void malformedJson_doesNotLeakParserDetails() {
        var response = as(OTHER_USER)
                .put()
                .uri("/users/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"bio\": ")
                .exchange();
        bodyExcludes(problem(response, 400, "error.request.unreadable"), "com.example", "JSON");
    }

    @Test
    void unsafeIncomingCorrelationId_isReplaced() {
        get("/users/999999", "X-Correlation-ID", "evil FAKE LOG LINE")
                .expectHeader()
                .value("X-Correlation-ID", not(containsString("evil")))
                .expectBody()
                .jsonPath("$.correlationId")
                .value(not(containsString("evil")));
    }

    @Test
    void correlationId_isInTheMdcOfApplicationLogs(CapturedOutput output) {
        get("/test-errors/other-thread", "X-Correlation-ID", "req-thread-7")
                .expectHeader()
                .valueEquals("X-Correlation-ID", "req-thread-7");
        assertThat(output.getOut()
                        .lines()
                        .filter(l -> l.contains("Running on another thread"))
                        .findFirst()
                        .orElseThrow())
                .contains("correlationId=req-thread-7");
    }
}
