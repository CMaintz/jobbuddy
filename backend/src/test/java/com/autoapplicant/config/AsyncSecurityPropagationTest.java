package com.autoapplicant.config;

import com.autoapplicant.adapter.security.SecurityContextCurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generation runs on the AI executor, and both the per-user API key and the usage log
 * find their user through SecurityContextHolder. A plain thread pool does not carry
 * that context across, which would silently send every generation through the server's
 * own provider and log none of it.
 */
class AsyncSecurityPropagationTest {

    private final AsyncConfig config = new AsyncConfig();
    private final Executor executor = config.aiTaskExecutor(config.aiTaskPool());
    private final SecurityContextCurrentUser currentUser = new SecurityContextCurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Optional<UUID> userSeenOnExecutor() throws Exception {
        AtomicReference<Optional<UUID>> seen = new AtomicReference<>();
        Thread waiter = new Thread(() -> { });
        executor.execute(() -> seen.set(currentUser.currentUserId()));
        // The pool has a bounded queue; give the task room to run.
        for (int i = 0; i < 100 && seen.get() == null; i++) Thread.sleep(20);
        waiter.join();
        return seen.get();
    }

    @Test
    void the_signed_in_user_reaches_the_generation_thread() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        assertThat(userSeenOnExecutor())
                .as("the user's own API key and their usage log both depend on this")
                .contains(userId);
    }

    @Test
    void the_same_holds_for_generation_started_with_a_completable_future() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        AtomicReference<Optional<UUID>> seen = new AtomicReference<>();

        java.util.concurrent.CompletableFuture
                .runAsync(() -> seen.set(currentUser.currentUserId()), config.requestBoundExecutor())
                .join();

        assertThat(seen.get()).contains(userId);
    }

    @Test
    void background_work_with_no_signed_in_user_still_sees_nobody() throws Exception {
        SecurityContextHolder.clearContext();

        assertThat(userSeenOnExecutor()).isEmpty();
    }
}
