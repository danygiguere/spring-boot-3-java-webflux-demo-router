package com.example.routerdemo.error;

/**
 * Sends server-side failures to an external error tracker such as Sentry.
 *
 * <p>{@link GlobalExceptionHandler} calls every {@code ErrorReporter} bean for each failure it logs at ERROR, and for
 * nothing else: 4xx errors and plain cancellations are expected and are not reported. With no bean registered, nothing
 * is reported. A reporter that throws is logged and ignored, so a broken tracker never changes the response.
 *
 * <p>Example with the Sentry Java SDK ({@code io.sentry:sentry}):
 *
 * <pre>{@code
 * @Component
 * class SentryErrorReporter implements ErrorReporter {
 *     @Override
 *     public void report(Throwable error, ErrorContext context) {
 *         Sentry.withScope(scope -> {
 *             if (context.correlationId() != null) scope.setTag("correlationId", context.correlationId());
 *             scope.setTag("http.method", context.method());
 *             scope.setTag("http.status", String.valueOf(context.status()));
 *             scope.setTag("error.code", context.code());
 *             scope.setExtra("path", context.path());
 *             Sentry.captureException(error);
 *         });
 *     }
 * }
 * }</pre>
 *
 * {@code captureException} only queues the event, so it is safe to call on a request thread. A reporter that does
 * blocking I/O must hand the work off to another thread itself.
 */
@FunctionalInterface
public interface ErrorReporter {

    void report(Throwable error, ErrorContext context);
}
