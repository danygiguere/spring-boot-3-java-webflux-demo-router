package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 400 with field errors, for rules Bean Validation cannot express: cross-field rules, uniqueness checks against the
 * database, and so on. The response uses the same {@code errors} format as a Bean Validation failure, so clients handle
 * both the same way.
 *
 * <pre>{@code
 * throw new FieldValidationException(
 *         List.of(new FieldViolation("password", "error.user.passwordContainsEmail")),
 *         "password contains the email local part");
 * }</pre>
 */
public class FieldValidationException extends ApiException {

    private final List<FieldViolation> violations;

    public FieldValidationException(List<FieldViolation> violations, String internalMessage) {
        super(HttpStatus.BAD_REQUEST, "error.validation", List.of(), internalMessage, null);
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("FieldValidationException needs at least one violation");
        }
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
