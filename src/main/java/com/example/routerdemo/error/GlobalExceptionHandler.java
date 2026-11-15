package com.example.routerdemo.error;

import com.example.routerdemo.filter.RequestLoggingFilter;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.CodecException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;
import reactor.core.publisher.Mono;

/**
 * The only place where an exception becomes an HTTP response.
 *
 * <p>It is a {@link WebExceptionHandler}, not a {@code @RestControllerAdvice}, so it sees every exception: from
 * controllers, from WebFilters (which a controller advice never sees), and from routing. It runs before Spring Boot's
 * own error handler (order -1), which therefore never answers a request. Spring Security's entry point and
 * access-denied handler delegate here too (see {@code SecurityConfig}).
 *
 * <p>Each exception goes through three steps:
 *
 * <ol>
 *   <li>{@link #classify}: decide the status, the public message key, and how to log it ({@link Failure}).
 *   <li>{@link #logAndReport}: log it once, with the correlation ID, method, and path. Server faults are logged at
 *       ERROR with the stack trace and passed to every {@link ErrorReporter}; nothing else is.
 *   <li>{@link #write}: send an RFC 9457 {@code application/problem+json} body. Its {@code title} and {@code detail}
 *       come from {@code messages*.properties} in the caller's locale, plus a stable {@code code} and the
 *       {@code correlationId}.
 * </ol>
 *
 * <p>Exception messages are never sent to the client. That covers ours, Spring's, Jackson's, and the JDK's. They go
 * to the log, where the {@code correlationId} links them to the response.
 */
@Component
@Order(-2)
public class GlobalExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_KEY = "error.validation";
    private static final String SERVICE_UNAVAILABLE_KEY = "error.serviceUnavailable";

    private final MessageTranslator translator;
    private final ValidationErrorTranslator validationErrors;
    private final ObjectProvider<ErrorReporter> reporters;

    /** Writes bodies with the application's own codecs, so JSON settings match normal responses. */
    private final ServerResponse.Context responseContext;

    public GlobalExceptionHandler(
            MessageTranslator translator,
            ValidationErrorTranslator validationErrors,
            ObjectProvider<ErrorReporter> reporters,
            ServerCodecConfigurer codecs) {
        this.translator = translator;
        this.validationErrors = validationErrors;
        this.reporters = reporters;
        this.responseContext = new ServerResponse.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return codecs.getWriters();
            }

            @Override
            public List<ViewResolver> viewResolvers() {
                return List.of();
            }
        };
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // The client is gone (broken pipe, connection reset): nobody to answer and nothing to fix.
        if (DisconnectedClientHelper.isClientDisconnectedException(ex)) {
            withCorrelationMdc(exchange, () -> log.debug("Client disconnected: {}", ex.toString()));
            return Mono.empty();
        }
        Locale locale = exchange.getLocaleContext().getLocale();
        if (locale == null) {
            locale = Locale.ENGLISH;
        }
        Failure failure = classify(ex, locale);
        logAndReport(exchange, ex, failure);
        if (exchange.getResponse().isCommitted()) {
            // Part of the response was already sent, so a problem body cannot be added. Let the server close the
            // connection.
            withCorrelationMdc(
                    exchange,
                    () -> log.warn("Response already committed; cannot write error body for {}", failure.key()));
            return Mono.error(ex);
        }
        return write(exchange, failure, locale);
    }

    /**
     * What to send and how to log it.
     *
     * @param key the public message key. Also sent as the stable {@code code}.
     * @param errors field errors, sent as {@code errors} for validation failures only
     * @param serverFault logged at ERROR with the stack trace and reported. Every 5xx by default.
     * @param logDetail extra text for the log line. Never sent to the client.
     * @param logStackTrace for a client error: log the stack trace too, because it wraps a cause
     */
    private record Failure(
            HttpStatusCode status,
            String key,
            List<Object> args,
            HttpHeaders headers,
            List<ValidationErrorItem> errors,
            boolean serverFault,
            String logDetail,
            boolean logStackTrace) {

        Failure(HttpStatusCode status, String key, String logDetail) {
            this(status, key, List.of(), HttpHeaders.EMPTY, null, status.is5xxServerError(), logDetail, false);
        }
    }

    // Step 1: classify.

    private Failure classify(Throwable ex, Locale locale) {
        // Our own timeout (Mono.timeout) expired: a real failure, and the client is still waiting.
        if (ex instanceof TimeoutException) {
            return new Failure(HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE_KEY, "reason=timeout");
        }
        // Work cancelled while the client is still connected: a shutdown in progress, or awaiting work other code
        // cancelled. Not a bug on its own: a rolling deploy produces many of these.
        if (ex instanceof CancellationException) {
            return new Failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    SERVICE_UNAVAILABLE_KEY,
                    List.of(),
                    HttpHeaders.EMPTY,
                    null,
                    false,
                    "reason=cancelled: " + ex.getMessage(),
                    false);
        }
        // Exceptions the application throws on purpose. FieldValidationException first: it is also an ApiException.
        if (ex instanceof FieldValidationException field) {
            // Without an internal message, Throwable.getMessage() is only the key: not worth logging.
            String reason = field.getMessage().equals(field.getMessageKey()) ? null : field.getMessage();
            return validationFailure(validationErrors.fromFieldViolations(field.getViolations(), locale), reason);
        }
        if (ex instanceof ApiException api) {
            return new Failure(
                    api.getStatus(),
                    api.getMessageKey(),
                    api.getPublicArgs(),
                    HttpHeaders.EMPTY,
                    null,
                    api.getStatus().is5xxServerError(),
                    "reason=" + api.getMessage(),
                    api.getCause() != null);
        }
        // Validation. These must come before ErrorResponse, which they implement.
        if (ex instanceof WebExchangeBindException bind) {
            return validationFailure(validationErrors.fromBinding(bind.getBindingResult(), locale), null);
        }
        if (ex instanceof HandlerMethodValidationException method) {
            return validationFailure(validationErrors.fromMethodValidation(method, locale), null);
        }
        if (ex instanceof ConstraintViolationException violations) {
            return validationFailure(
                    validationErrors.fromViolations(violations.getConstraintViolations(), locale), null);
        }
        // Security and persistence exceptions that are not bugs. Spring Security's entry point and access-denied
        // handler pass theirs here, as does method security (@PreAuthorize).
        if (ex instanceof AccessDeniedException) {
            return new Failure(HttpStatus.FORBIDDEN, "error.forbidden", describe(ex));
        }
        if (ex instanceof AuthenticationException) {
            return new Failure(HttpStatus.UNAUTHORIZED, "error.unauthenticated", describe(ex));
        }
        // @Version check failed: another request saved first. The client should reload and retry.
        if (ex instanceof OptimisticLockingFailureException) {
            return new Failure(HttpStatus.CONFLICT, "error.conflict", describe(ex));
        }
        // Body larger than spring.codec.max-in-memory-size, when not wrapped by Spring.
        if (ex instanceof DataBufferLimitException) {
            return new Failure(HttpStatus.PAYLOAD_TOO_LARGE, "error.request.payloadTooLarge", describe(ex));
        }
        // Spring's own exceptions: 404, 405, 415, unreadable body, type mismatch, and so on, plus
        // ResponseStatusException. Their text may echo client input or a developer's reason, so it is replaced with a
        // translated message. Their headers are kept (for example Allow on 405).
        if (ex instanceof ErrorResponse response) {
            return frameworkFailure(response);
        }
        return unexpected(ex);
    }

    private Failure validationFailure(List<ValidationErrorItem> errors, String reason) {
        // Log field names and constraint codes only. Never log rejected values.
        String fields = "fields="
                + errors.stream()
                        .map(e -> (e.field() != null ? e.field() : "<object>") + ":" + e.code())
                        .toList();
        return new Failure(
                HttpStatus.BAD_REQUEST,
                VALIDATION_KEY,
                List.of(),
                HttpHeaders.EMPTY,
                errors,
                false,
                fields + (reason != null ? " reason=" + reason : ""),
                false);
    }

    /**
     * Maps a Spring exception to a public message key. Arguments are limited to names the API itself defines, such as
     * parameter or header names. Values the client sent are never used.
     *
     * <p>WebFlux reports most request problems as a {@link ServerWebInputException}, so the cause and the method
     * parameter decide which message applies.
     */
    private Failure frameworkFailure(ErrorResponse ex) {
        HttpStatusCode status = ex.getStatusCode();
        String key;
        List<Object> args = List.of();
        if (ex instanceof MissingRequestValueException missing) {
            key = "header".equals(missing.getLabel())
                    ? "error.request.missingHeader"
                    : "error.request.missingParameter";
            args = List.of(missing.getName());
        } else if (ex instanceof ServerWebInputException input && hasCause(input, DataBufferLimitException.class)) {
            key = "error.request.payloadTooLarge";
            status = HttpStatus.PAYLOAD_TOO_LARGE;
        } else if (ex instanceof ServerWebInputException input && input.getCause() instanceof TypeMismatchException) {
            String name = ValidationErrorTranslator.apiName(input.getMethodParameter());
            key = name != null ? "error.request.typeMismatch" : genericKey(status);
            args = name != null ? List.of(name) : List.of();
        } else if (ex instanceof ServerWebInputException input
                && (hasCause(input, CodecException.class)
                        || (input.getMethodParameter() != null
                                && input.getMethodParameter().hasParameterAnnotation(RequestBody.class)))) {
            key = "error.request.unreadable";
        } else if (ex instanceof MethodNotAllowedException) {
            key = "error.request.methodNotSupported";
        } else if (ex instanceof UnsupportedMediaTypeStatusException) {
            key = "error.request.mediaTypeNotSupported";
        } else if (ex instanceof NotAcceptableStatusException) {
            key = "error.request.notAcceptable";
        } else if (ex instanceof NoResourceFoundException) {
            key = "error.notFound";
        } else {
            key = genericKey(status);
        }
        return new Failure(
                status, key, args, ex.getHeaders(), null, status.is5xxServerError(), describe((Throwable) ex), false);
    }

    /**
     * Anything unexpected becomes a generic 500. Exceptions annotated with {@code @ResponseStatus} keep their status
     * but still get a generic message.
     */
    private Failure unexpected(Throwable ex) {
        ResponseStatus annotation = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        HttpStatus status = annotation != null ? annotation.code() : HttpStatus.INTERNAL_SERVER_ERROR;
        return new Failure(status, genericKey(status), describe(ex));
    }

    // Step 2: log and report.

    private void logAndReport(ServerWebExchange exchange, Throwable ex, Failure failure) {
        var request = exchange.getRequest();
        // The path never includes the query string, which may hold tokens.
        String line = "Request failed status=" + failure.status().value() + " code=" + failure.key() + " method="
                + request.getMethod() + " path=" + request.getPath().value()
                + (failure.logDetail() != null ? " " + failure.logDetail() : "");
        withCorrelationMdc(exchange, () -> {
            if (failure.serverFault()) {
                log.error(line, ex);
            } else if (failure.logStackTrace()) {
                log.warn(line, ex);
            } else {
                log.warn(line);
            }
        });
        if (failure.serverFault()) {
            report(exchange, ex, failure);
        }
    }

    private void report(ServerWebExchange exchange, Throwable ex, Failure failure) {
        ErrorContext context = new ErrorContext(
                RequestLoggingFilter.currentCorrelationId(exchange),
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().value(),
                failure.status().value(),
                failure.key());
        reporters.orderedStream().forEach(reporter -> {
            try {
                reporter.report(ex, context);
            } catch (Exception reportFailure) {
                // A broken error tracker must never change the response.
                withCorrelationMdc(
                        exchange,
                        () -> log.warn(
                                "Error reporter {} failed", reporter.getClass().getName(), reportFailure));
            }
        });
    }

    // Step 3: write.

    private Mono<Void> write(ServerWebExchange exchange, Failure failure, Locale locale) {
        HttpStatusCode status = failure.status();
        ProblemDetail problem = ProblemDetail.forStatus(status);
        String title = translator.translate("error.title." + status.value(), List.of(), locale);
        if (title == null) {
            HttpStatus known = HttpStatus.resolve(status.value());
            title = known != null ? known.getReasonPhrase() : "Error";
        }
        problem.setTitle(title);
        String detail = translator.translate(failure.key(), failure.args(), locale);
        problem.setDetail(detail != null ? detail : translator.translate(genericKey(status), List.of(), locale));
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        problem.setProperty("code", failure.key());
        String correlationId = RequestLoggingFilter.currentCorrelationId(exchange);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        if (failure.errors() != null) {
            problem.setProperty("errors", failure.errors());
        }
        return ServerResponse.status(status)
                .headers(headers -> headers.addAll(failure.headers()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(problem)
                .flatMap(response -> response.writeTo(exchange, responseContext));
    }

    // Helpers.

    /** Exception class and message, for the log only. */
    private static String describe(Throwable ex) {
        return "exception=" + ex.getClass().getName() + ": " + ex.getMessage();
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static String genericKey(HttpStatusCode status) {
        int value = status.value();
        return switch (value) {
            case 400 -> "error.badRequest";
            case 401 -> "error.unauthenticated";
            case 403 -> "error.forbidden";
            case 404 -> "error.notFound";
            case 405 -> "error.request.methodNotSupported";
            case 409 -> "error.conflict";
            case 413 -> "error.request.payloadTooLarge";
            case 415 -> "error.request.mediaTypeNotSupported";
            case 422 -> "error.businessRule";
            case 503 -> SERVICE_UNAVAILABLE_KEY;
            default -> value >= 400 && value < 500 ? "error.badRequest" : "error.internal";
        };
    }

    /**
     * This handler runs on whatever thread completed the reactive chain, so the MDC may not hold this request's
     * correlation ID. It is restored from the exchange for the duration of the call.
     */
    private static void withCorrelationMdc(ServerWebExchange exchange, Runnable block) {
        String correlationId = RequestLoggingFilter.currentCorrelationId(exchange);
        if (correlationId == null) {
            block.run();
            return;
        }
        try (var ignored = MDC.putCloseable(RequestLoggingFilter.MDC_CORRELATION_ID, correlationId)) {
            block.run();
        }
    }
}
