package com.example.routerdemo.service;

import com.example.routerdemo.dto.EnterpriseResponse;
import com.example.routerdemo.exception.AppException;
import com.example.routerdemo.repository.EnterpriseRepository;
import com.example.routerdemo.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class EnterpriseService {

    private final EnterpriseRepository enterpriseRepository;

    public EnterpriseService(EnterpriseRepository enterpriseRepository) {
        this.enterpriseRepository = enterpriseRepository;
    }

    /** The caller's enterprise: the only one they can see. */
    public Mono<EnterpriseResponse> current(AuthenticatedUser me) {
        return enterpriseRepository
                .findById(me.enterpriseId())
                .switchIfEmpty(Mono.error(() -> new AppException.NotFound("error.enterprise.not.found")))
                .map(EnterpriseResponse::of);
    }
}
