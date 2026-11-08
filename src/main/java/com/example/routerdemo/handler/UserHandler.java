package com.example.routerdemo.handler;

import com.example.routerdemo.dto.PostSummary;
import com.example.routerdemo.dto.ProfileRequest;
import com.example.routerdemo.dto.UserResponse;
import com.example.routerdemo.security.CurrentUser;
import com.example.routerdemo.service.PostService;
import com.example.routerdemo.service.UserService;
import com.example.routerdemo.web.RequestValidator;
import com.example.routerdemo.web.Requests;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class UserHandler {

    private final UserService userService;
    private final PostService postService;
    private final CurrentUser currentUser;
    private final RequestValidator validator;

    public UserHandler(
            UserService userService, PostService postService, CurrentUser currentUser, RequestValidator validator) {
        this.userService = userService;
        this.postService = postService;
        this.currentUser = currentUser;
        this.validator = validator;
    }

    public Mono<ServerResponse> me(ServerRequest request) {
        return currentUser.get().flatMap(userService::me).flatMap(me -> ServerResponse.ok()
                .bodyValue(me));
    }

    public Mono<ServerResponse> findAll(ServerRequest request) {
        long afterId = Requests.afterId(request);
        int limit = Requests.limit(request);
        return currentUser.get().flatMap(me -> ServerResponse.ok()
                .body(userService.findAll(me, afterId, limit), UserResponse.class));
    }

    public Mono<ServerResponse> findById(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return currentUser.get().flatMap(me -> userService.findById(me, id)).flatMap(user -> ServerResponse.ok()
                .bodyValue(user));
    }

    public Mono<ServerResponse> findProfile(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return currentUser.get().flatMap(me -> userService.findProfile(me, id)).flatMap(profile -> ServerResponse.ok()
                .bodyValue(profile));
    }

    public Mono<ServerResponse> replaceMyProfile(ServerRequest request) {
        return currentUser
                .get()
                .flatMap(me -> validator
                        .body(request, ProfileRequest.class)
                        .flatMap(body -> userService.replaceMyProfile(me, body)))
                .flatMap(profile -> ServerResponse.ok().bodyValue(profile));
    }

    public Mono<ServerResponse> findPosts(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        long beforeId = Requests.beforeId(request);
        int limit = Requests.limit(request);
        return currentUser.get().flatMap(me -> ServerResponse.ok()
                .body(postService.findByAuthor(me, id, beforeId, limit), PostSummary.class));
    }

    public Mono<ServerResponse> findLikedPosts(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        long beforeId = Requests.beforeId(request);
        int limit = Requests.limit(request);
        return currentUser.get().flatMap(me -> ServerResponse.ok()
                .body(postService.findLikedBy(me, id, beforeId, limit), PostSummary.class));
    }
}
