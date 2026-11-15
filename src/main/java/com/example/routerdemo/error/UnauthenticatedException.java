package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 401: credentials are missing or invalid. */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException() {
        this("error.unauthenticated");
    }

    public UnauthenticatedException(String messageKey, Object... publicArgs) {
        this(messageKey, List.of(publicArgs), null, null);
    }

    public UnauthenticatedException(String messageKey, List<?> publicArgs, String internalMessage, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, messageKey, publicArgs, internalMessage, cause);
    }
}
