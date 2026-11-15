package com.example.routerdemo.handler;

import com.example.routerdemo.dto.ProfileRequest;
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
        long cursor = Requests.afterCursor(request);
        int limit = Requests.limit(request);
        return userService.findAll(cursor, limit).flatMap(page -> ServerResponse.ok()
                .bodyValue(page));
    }

    public Mono<ServerResponse> findById(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return userService.findById(id).flatMap(user -> ServerResponse.ok().bodyValue(user));
    }

    public Mono<ServerResponse> findProfile(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return userService.findProfile(id).flatMap(profile -> ServerResponse.ok()
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
        long cursor = Requests.beforeCursor(request);
        int limit = Requests.limit(request);
        return postService.findByAuthor(id, cursor, limit).flatMap(page -> ServerResponse.ok()
                .bodyValue(page));
    }

    public Mono<ServerResponse> findLikedPosts(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        long cursor = Requests.beforeCursor(request);
        int limit = Requests.limit(request);
        return postService.findLikedBy(id, cursor, limit).flatMap(page -> ServerResponse.ok()
                .bodyValue(page));
    }

    public Mono<ServerResponse> findImages(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        long cursor = Requests.beforeCursor(request);
        int limit = Requests.limit(request);
        return userService.findImages(id, cursor, limit).flatMap(page -> ServerResponse.ok()
                .bodyValue(page));
    }
}
