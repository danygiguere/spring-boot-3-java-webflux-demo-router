package com.example.routerdemo.error;

/**
 * One validation failure in a 400 response.
 *
 * <p>The rejected value is left out on purpose. It may be a password, a token, or PII, and the caller already knows
 * what it sent.
 *
 * @param field the request field or parameter name, or null for an object-level error
 * @param code the constraint name (for example {@code NotBlank}), which clients can match on
 * @param message the message translated into the caller's locale
 */
public record ValidationErrorItem(String field, String code, String message) {}
