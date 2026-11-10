package com.example.routerdemo.handler;

import com.example.routerdemo.dto.CreatePostRequest;
import com.example.routerdemo.dto.ImageResponse;
import com.example.routerdemo.dto.PostSummary;
import com.example.routerdemo.dto.UpdatePostRequest;
import com.example.routerdemo.dto.UserResponse;
import com.example.routerdemo.exception.AppException;
import com.example.routerdemo.security.CurrentUser;
import com.example.routerdemo.service.PostService;
import com.example.routerdemo.web.RequestValidator;
import com.example.routerdemo.web.Requests;
import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class PostHandler {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,128}");

    private final PostService postService;
    private final CurrentUser currentUser;
    private final RequestValidator validator;

    public PostHandler(PostService postService, CurrentUser currentUser, RequestValidator validator) {
        this.postService = postService;
        this.currentUser = currentUser;
        this.validator = validator;
    }

    public Mono<ServerResponse> feed(ServerRequest request) {
        long beforeId = Requests.beforeId(request);
        int limit = Requests.limit(request);
        return ServerResponse.ok().body(postService.feed(beforeId, limit), PostSummary.class);
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        String idempotencyKey = idempotencyKey(request);
        return currentUser
                .get()
                .flatMap(me -> validator
                        .body(request, CreatePostRequest.class)
                        .flatMap(body -> postService.create(me, idempotencyKey, body)))
                .flatMap(created -> ServerResponse.created(
                                URI.create("/posts/" + created.post().id()))
                        .header(REPLAYED_HEADER, String.valueOf(created.replayed()))
                        .bodyValue(created.post()));
    }

    public Mono<ServerResponse> findById(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return postService.findById(id).flatMap(post -> ServerResponse.ok().bodyValue(post));
    }

    public Mono<ServerResponse> update(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return currentUser
                .get()
                .flatMap(me -> validator
                        .body(request, UpdatePostRequest.class)
                        .flatMap(body -> postService.update(me, id, body)))
                .flatMap(post -> ServerResponse.ok().bodyValue(post));
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return currentUser
                .get()
                .flatMap(me -> postService.delete(me, id))
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> findImages(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return ServerResponse.ok().body(postService.findImages(id), ImageResponse.class);
    }

    public Mono<ServerResponse> findLikers(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        long afterId = Requests.afterId(request);
        int limit = Requests.limit(request);
        return ServerResponse.ok().body(postService.findLikers(id, afterId, limit), UserResponse.class);
    }

    public Mono<ServerResponse> like(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return currentUser
                .get()
                .flatMap(me -> postService.like(me, id))
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> unlike(ServerRequest request) {
        long id = Requests.pathId(request, "id");
        return currentUser
                .get()
                .flatMap(me -> postService.unlike(me, id))
                .then(ServerResponse.noContent().build());
    }

    /** Required: without it, a retried or double-submitted create would publish the post twice. */
    private static String idempotencyKey(ServerRequest request) {
        String key = request.headers().firstHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            throw new AppException.BadRequest("error.header.required", IDEMPOTENCY_KEY_HEADER);
        }
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new AppException.BadRequest("error.idempotency.key.invalid");
        }
        return key;
    }
}
