package com.example.routerdemo.handler;

import static org.hamcrest.Matchers.hasItem;

import com.example.routerdemo.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class UserHandlerTest extends IntegrationTest {

    @Test
    void me_returnsTheTokenUserWithEmail() {
        as(ALICE)
                .get()
                .uri("/users/me")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(1)
                .jsonPath("$.email")
                .isEqualTo(ALICE);
    }

    @Test
    void list_isKeysetPaginated() {
        as(ALICE)
                .get()
                .uri("/users?cursor=1&limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.length()")
                .isEqualTo(3)
                .jsonPath("$.data[0].id")
                .isEqualTo(2)
                .jsonPath("$.data[2].id")
                .isEqualTo(4)
                .jsonPath("$.nextCursor")
                .isEqualTo(4)
                // Other users are listed without their email.
                .jsonPath("$.data[0].email")
                .doesNotExist();
    }

    @Test
    void list_lastPageHasNoNextCursor() {
        // 50 users: a short page and an exactly full page both end the list.
        as(ALICE)
                .get()
                .uri("/users?cursor=48&limit=5")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[*].id")
                .isEqualTo(java.util.List.of(49, 50))
                .jsonPath("$.nextCursor")
                .isEmpty();
        as(ALICE)
                .get()
                .uri("/users?cursor=47&limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[*].id")
                .isEqualTo(java.util.List.of(48, 49, 50))
                .jsonPath("$.nextCursor")
                .isEmpty();
    }

    @Test
    void list_rejectsLimitAboveMaximum() {
        as(ALICE)
                .get()
                .uri("/users?limit=500")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("error.request.invalidQueryParameter")
                .jsonPath("$.detail")
                .isEqualTo("Query parameter ‘limit’ must be a number between 1 and 100.");
    }

    @Test
    void findById_unknownUser_isNotFoundWithLocalizedDetail() {
        as(ALICE)
                .get()
                .uri("/users/999999")
                .header("Accept-Language", "fr")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("error.user.notFound")
                .jsonPath("$.detail")
                .isEqualTo("Utilisateur 999999 introuvable.")
                .jsonPath("$.instance")
                .isEqualTo("/users/999999");
    }

    @Test
    void findById_nonNumericId_returns400() {
        as(ALICE)
                .get()
                .uri("/users/abc")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("error.request.invalidPathParameter");
    }

    @Test
    void profile_isReadableByOtherUsers() {
        as(BOB).get()
                .uri("/users/1/profile")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.userId")
                .isEqualTo(1)
                .jsonPath("$.bio")
                .isEqualTo("Bio of user 1");
    }

    @Test
    void replaceMyProfile_upsertsTheCallersProfileOnly() {
        WebTestClient user = as(OTHER_USER);
        for (int attempt = 0; attempt < 2; attempt++) {
            user.put()
                    .uri("/users/me/profile")
                    .bodyValue(Map.of("bio", "Updated bio", "avatarUrl", "https://images.example.com/u4.png"))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.userId")
                    .isEqualTo(4)
                    .jsonPath("$.bio")
                    .isEqualTo("Updated bio");
        }
    }

    @Test
    void replaceMyProfile_rejectsNonHttpsAvatar() {
        as(OTHER_USER)
                .put()
                .uri("/users/me/profile")
                .header("Accept-Language", "fr")
                .bodyValue(Map.of("avatarUrl", "javascript:alert(1)"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("error.validation")
                .jsonPath("$.errors[?(@.field == 'avatarUrl')].message")
                .value(hasItem("L’URL de l’avatar doit être une URL https valide"));
    }

    @Test
    void replaceMyProfile_rejectsUnknownFields() {
        as(OTHER_USER)
                .put()
                .uri("/users/me/profile")
                .bodyValue(Map.of("bio", "x", "userId", 1))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void posts_areTheUsersPostsNewestFirst() {
        as(BOB).get()
                .uri("/users/1/posts")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[*].id")
                .isEqualTo(java.util.List.of(4, 3, 2, 1))
                .jsonPath("$.data[0].author.name")
                .isEqualTo("Alice")
                .jsonPath("$.nextCursor")
                .isEmpty();
    }

    @Test
    void posts_nextCursorContinuesTheList() {
        as(BOB).get()
                .uri("/users/1/posts?limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[*].id")
                .isEqualTo(java.util.List.of(4, 3, 2))
                .jsonPath("$.nextCursor")
                .isEqualTo(2);
        as(BOB).get()
                .uri("/users/1/posts?cursor=2&limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[*].id")
                .isEqualTo(java.util.List.of(1))
                .jsonPath("$.nextCursor")
                .isEmpty();
    }

    @Test
    void posts_ofUnknownUser_isNotFound() {
        as(ALICE)
                .get()
                .uri("/users/999999/posts")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("error.user.notFound");
    }

    @Test
    void images_areTheImagesOfTheUsersPostsNewestFirst() {
        // Alice's posts 1 to 3 have 1, 2 and 3 images: ids 1 to 6.
        as(BOB).get()
                .uri("/users/1/images?cursor=6&limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[*].id")
                .isEqualTo(java.util.List.of(5, 4, 3))
                .jsonPath("$.data[*].postId")
                .isEqualTo(java.util.List.of(3, 3, 2))
                .jsonPath("$.nextCursor")
                .isEqualTo(3);
    }

    @Test
    void images_ofUnknownUser_isNotFound() {
        as(ALICE)
                .get()
                .uri("/users/999999/images")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("error.user.notFound");
    }
}
