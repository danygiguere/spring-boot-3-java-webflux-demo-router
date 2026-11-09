package com.example.routerdemo.router;

import com.example.routerdemo.dto.DevTokenRequest;
import com.example.routerdemo.dto.TokenResponse;
import com.example.routerdemo.handler.AuthHandler;
import com.example.routerdemo.security.DevTokenEnabled;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
@DevTokenEnabled
public class AuthRouter {

    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/auth/dev-token",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "devToken",
                operation =
                        @Operation(
                                operationId = "devToken",
                                summary = "Get a token for a seeded user, without password (DEV_TOKEN_ENABLED only)",
                                description = "Seeded users: alice@example.com, bob@example.com (enterprise 1), "
                                        + "eve@example.com (enterprise 2).",
                                requestBody =
                                        @RequestBody(
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        DevTokenRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
                                    @ApiResponse(responseCode = "401", description = "Unknown email")
                                }))
    })
    public RouterFunction<ServerResponse> authRoutes(AuthHandler handler) {
        return RouterFunctions.route()
                .POST("/auth/dev-token", handler::devToken)
                .build();
    }
}
