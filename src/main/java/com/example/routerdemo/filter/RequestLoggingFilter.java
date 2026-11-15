package com.example.routerdemo.filter;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gives every request a correlation ID and puts request details in the MDC for every log line.
 *
 * <ul>
 *   <li>The ID is echoed in the {@value #CORRELATION_ID_HEADER} response header and in error bodies, so a user can
 *       report it and an operator can find the matching log lines, including internal messages the client never sees.
 *   <li>MDC keys: {@code correlationId}, {@code requestUri}, {@code requestMethod}.
 * </ul>
 *
 * <p>WebFlux has no thread per request, so putting values in the MDC of the current thread is not enough: the chain
 * hops between threads. Instead, the values go into the Reactor context, and Micrometer context propagation
 * ({@code spring.reactor.context-propagation: auto}) restores them into the MDC, through {@link MdcAccessor}, on
 * whatever thread runs each operator. The ID is also stored as an exchange attribute, which the exception handler
 * reads.
 *
 * <p>An incoming {@value #CORRELATION_ID_HEADER} is reused only if it matches a strict pattern. Anything else is
 * replaced, so a client cannot inject newlines or fake entries into the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_ID = "correlationId";
    private static final String ATTRIBUTE = RequestLoggingFilter.class.getName() + ".correlationId";
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{1,64}$");

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new MdcAccessor());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String correlationId =
                incoming != null && CORRELATION_ID_PATTERN.matcher(incoming).matches() ? incoming : newCorrelationId();
        exchange.getAttributes().put(ATTRIBUTE, correlationId);
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        Map<String, String> mdc = new LinkedHashMap<>();
        mdc.put(MDC_CORRELATION_ID, correlationId);
        mdc.put("requestUri", exchange.getRequest().getPath().value()); // never the query string, which may hold tokens
        mdc.put("requestMethod", exchange.getRequest().getMethod().name());
        return chain.filter(exchange).contextWrite(context -> context.put(MdcAccessor.KEY, mdc));
    }

    /**
     * Correlation IDs need to be unique, not unpredictable. {@code UUID.randomUUID()} reads the platform SecureRandom,
     * which can block the event loop. ThreadLocalRandom does no I/O.
     */
    public static String newCorrelationId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new UUID(random.nextLong(), random.nextLong()).toString();
    }

    public static String currentCorrelationId(ServerWebExchange exchange) {
        return exchange.getAttribute(ATTRIBUTE);
    }

    /** Copies the request's MDC values between the Reactor context and the MDC of the current thread. */
    static final class MdcAccessor implements ThreadLocalAccessor<Map<String, String>> {

        static final String KEY = "request.mdc";
        private static final String[] KEYS = {MDC_CORRELATION_ID, "requestUri", "requestMethod"};

        @Override
        public Object key() {
            return KEY;
        }

        @Override
        public Map<String, String> getValue() {
            String correlationId = MDC.get(MDC_CORRELATION_ID);
            if (correlationId == null) {
                return null;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (String key : KEYS) {
                String value = MDC.get(key);
                if (value != null) {
                    values.put(key, value);
                }
            }
            return values;
        }

        @Override
        public void setValue(Map<String, String> value) {
            value.forEach(MDC::put);
        }

        @Override
        public void setValue() {
            for (String key : KEYS) {
                MDC.remove(key);
            }
        }
    }
}
