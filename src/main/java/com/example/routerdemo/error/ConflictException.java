package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 409: a duplicate, an optimistic-lock failure, or a state conflict. */
public class ConflictException extends ApiException {

    public ConflictException() {
        this("error.conflict");
    }

    public ConflictException(String messageKey, Object... publicArgs) {
        this(messageKey, List.of(publicArgs), null, null);
    }

    public ConflictException(String messageKey, List<?> publicArgs, String internalMessage, Throwable cause) {
        super(HttpStatus.CONFLICT, messageKey, publicArgs, internalMessage, cause);
    }
}
