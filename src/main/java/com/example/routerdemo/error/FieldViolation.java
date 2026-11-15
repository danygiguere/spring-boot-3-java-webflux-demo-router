package com.example.routerdemo.error;

import java.util.List;

/**
 * One field error raised by application code.
 *
 * @param field the request field, or null for an error on the whole object
 * @param messageKey the public message key, translated like any other
 * @param publicArgs arguments for the message. The same rules as {@link ApiException#getPublicArgs()} apply.
 * @param code the stable identifier clients can match on. Defaults to the message key.
 */
public record FieldViolation(String field, String messageKey, List<Object> publicArgs, String code) {

    public FieldViolation {
        publicArgs = List.copyOf(publicArgs);
    }

    public FieldViolation(String field, String messageKey, Object... publicArgs) {
        this(field, messageKey, List.of(publicArgs), messageKey);
    }
}
