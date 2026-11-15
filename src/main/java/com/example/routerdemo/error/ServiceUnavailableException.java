package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 503: a dependency is unavailable. The client always gets a generic message. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String internalMessage, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "error.serviceUnavailable", List.of(), internalMessage, cause);
    }
}
