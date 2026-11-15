package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 400: the request is invalid in a way bean validation cannot express. */
public class BadRequestException extends ApiException {

    public BadRequestException() {
        this("error.badRequest");
    }

    public BadRequestException(String messageKey, Object... publicArgs) {
        this(messageKey, List.of(publicArgs), null, null);
    }

    public BadRequestException(String messageKey, List<?> publicArgs, String internalMessage, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, messageKey, publicArgs, internalMessage, cause);
    }
}
