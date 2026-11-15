package com.example.routerdemo.service;

import com.example.routerdemo.domain.entity.User;
import com.example.routerdemo.dto.CursorPage;
import com.example.routerdemo.dto.ImageResponse;
import com.example.routerdemo.dto.MeResponse;
import com.example.routerdemo.dto.ProfileRequest;
import com.example.routerdemo.dto.ProfileResponse;
import com.example.routerdemo.dto.UserResponse;
import com.example.routerdemo.error.NotFoundException;
import com.example.routerdemo.repository.ImageRepository;
import com.example.routerdemo.repository.ProfileRepository;
import com.example.routerdemo.repository.UserRepository;
import com.example.routerdemo.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ImageRepository imageRepository;

    public UserService(
            UserRepository userRepository, ProfileRepository profileRepository, ImageRepository imageRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.imageRepository = imageRepository;
    }

    public Mono<MeResponse> me(AuthenticatedUser me) {
        return requireUser(me.userId()).map(MeResponse::of);
    }

    public Mono<CursorPage<UserResponse>> findAll(long cursor, int limit) {
        return userRepository
                .findPage(cursor, limit + 1)
                .map(UserResponse::of)
                .collectList()
                .map(rows -> CursorPage.of(rows, limit, UserResponse::id));
    }

    public Mono<UserResponse> findById(long id) {
        return requireUser(id).map(UserResponse::of);
    }

    public Mono<ProfileResponse> findProfile(long userId) {
        return requireUser(userId)
                .flatMap(user -> profileRepository.findByUserId(user.id()))
                .switchIfEmpty(Mono.error(() -> new NotFoundException("error.profile.notFound", userId)))
                .map(ProfileResponse::of);
    }

    /** Only the caller's own profile is writable; the target comes from the token, not the request. */
    public Mono<ProfileResponse> replaceMyProfile(AuthenticatedUser me, ProfileRequest request) {
        return requireUser(me.userId())
                .flatMap(user -> profileRepository.upsert(user.id(), request.bio(), request.avatarUrl()))
                .map(ProfileResponse::of);
    }

    /** The existence check runs first, so a 404 is raised before any byte is written. */
    public Mono<CursorPage<ImageResponse>> findImages(long userId, long cursor, int limit) {
        return requireUser(userId)
                .thenMany(imageRepository.findByUser(userId, cursor, limit + 1))
                .map(ImageResponse::of)
                .collectList()
                .map(rows -> CursorPage.of(rows, limit, ImageResponse::id));
    }

    public Mono<User> requireUser(long userId) {
        return userRepository
                .findById(userId)
                .switchIfEmpty(Mono.error(() -> new NotFoundException("error.user.notFound", userId)));
    }
}
