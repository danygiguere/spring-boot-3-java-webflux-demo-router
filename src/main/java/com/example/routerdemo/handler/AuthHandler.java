package com.example.routerdemo.handler;

import com.example.routerdemo.dto.DevTokenRequest;
import com.example.routerdemo.security.DevTokenEnabled;
import com.example.routerdemo.service.DevTokenService;
import com.example.routerdemo.web.RequestValidator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@DevTokenEnabled
public class AuthHandler {

    private final DevTokenService devTokenService;
    private final RequestValidator validator;

    public AuthHandler(DevTokenService devTokenService, RequestValidator validator) {
        this.devTokenService = devTokenService;
        this.validator = validator;
    }

    public Mono<ServerResponse> devToken(ServerRequest request) {
        return validator
                .body(request, DevTokenRequest.class)
                .flatMap(body -> devTokenService.issue(body.email()))
                .flatMap(token -> ServerResponse.ok().bodyValue(token));
    }
}
