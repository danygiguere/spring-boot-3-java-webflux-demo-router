package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 404: the resource does not exist, or the caller is not allowed to know that it exists. */
public class NotFoundException extends ApiException {

    public NotFoundException() {
        this("error.notFound");
    }

    public NotFoundException(String messageKey, Object... publicArgs) {
        this(messageKey, List.of(publicArgs), null, null);
    }

    public NotFoundException(String messageKey, List<?> publicArgs, String internalMessage, Throwable cause) {
        super(HttpStatus.NOT_FOUND, messageKey, publicArgs, internalMessage, cause);
    }
}
