package com.example.routerdemo.web;

import com.example.routerdemo.exception.AppException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

/**
 * Reads and validates a request body. Router handlers have no {@code @Valid}: this is its functional counterpart.
 * Violations become a {@link ConstraintViolationException}, rendered as a 422 by the error handler.
 */
@Component
public class RequestValidator {

    private final Validator validator;

    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

    /** The decoded, validated body; a 400 when the body is missing. */
    public <T> Mono<T> body(ServerRequest request, Class<T> type) {
        Locale locale = Requests.locale(request);
        return request.bodyToMono(type)
                .switchIfEmpty(Mono.error(() -> new AppException.BadRequest("error.body.required")))
                .map(body -> validate(body, locale));
    }

    /** Synchronous: the locale is set and reset around the call, on the same thread. */
    public <T> T validate(T target, Locale locale) {
        LocaleContextHolder.setLocaleContext(new SimpleLocaleContext(locale));
        try {
            Set<ConstraintViolation<T>> violations = validator.validate(target);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            return target;
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
