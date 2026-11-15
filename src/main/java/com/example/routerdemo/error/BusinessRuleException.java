package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 422: the request is well formed but breaks a business rule. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException() {
        this("error.businessRule");
    }

    public BusinessRuleException(String messageKey, Object... publicArgs) {
        this(messageKey, List.of(publicArgs), null, null);
    }

    public BusinessRuleException(String messageKey, List<?> publicArgs, String internalMessage, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, messageKey, publicArgs, internalMessage, cause);
    }
}
