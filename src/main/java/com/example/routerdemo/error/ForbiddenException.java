package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 403: the caller is authenticated but lacks permission for an action. */
public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        this("error.forbidden");
    }

    public ForbiddenException(String messageKey, Object... publicArgs) {
        this(messageKey, List.of(publicArgs), null, null);
    }

    public ForbiddenException(String messageKey, List<?> publicArgs, String internalMessage, Throwable cause) {
        super(HttpStatus.FORBIDDEN, messageKey, publicArgs, internalMessage, cause);
    }
}
