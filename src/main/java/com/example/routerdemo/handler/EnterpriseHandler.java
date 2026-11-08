package com.example.routerdemo.handler;

import com.example.routerdemo.security.CurrentUser;
import com.example.routerdemo.service.EnterpriseService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class EnterpriseHandler {

    private final EnterpriseService enterpriseService;
    private final CurrentUser currentUser;

    public EnterpriseHandler(EnterpriseService enterpriseService, CurrentUser currentUser) {
        this.enterpriseService = enterpriseService;
        this.currentUser = currentUser;
    }

    public Mono<ServerResponse> current(ServerRequest request) {
        return currentUser.get().flatMap(enterpriseService::current).flatMap(enterprise -> ServerResponse.ok()
                .bodyValue(enterprise));
    }
}
