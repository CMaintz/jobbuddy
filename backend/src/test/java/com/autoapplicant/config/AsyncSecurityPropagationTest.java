package com.autoapplicant.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.security.SecurityContextCurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Generation runs on the AI executors, and both the per-user API key and the usage log
 * find their user through SecurityContextHolder. A plain thread pool does not carry
 * that context across, which would silently send every generation through the server's
 * own provider and log none of it.
 */
class AsyncSecurityPropagationTest {

    private static final long TIMEOUT_SECONDS = 5;

    private final AsyncConfig config = new AsyncConfig();
    private final ThreadPoolTaskExecutor enrichmentPool = config.aiTaskPool();
    private final ThreadPoolTaskExecutor userPool = config.userAiTaskPool();
    private final Executor enrichmentExecutor = config.aiTaskExecutor(enrichmentPool);
    private final Executor userExecutor = config.userAiTaskExecutor(userPool);
    private final SecurityContextCurrentUser currentUser = new SecurityContextCurrentUser();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        enrichmentPool.shutdown();
        userPool.shutdown();
    }

    private Optional<UUID> userSeenOn(Executor executor) throws Exception {
        return CompletableFuture.supplyAsync(currentUser::currentUserId, executor)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void signIn(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void the_signed_in_user_reaches_the_generation_thread() throws Exception {
        UUID userId = UUID.randomUUID();
        signIn(userId);

        assertThat(userSeenOn(userExecutor))
                .as("the user's own API key and their usage log both depend on this")
                .contains(userId);
    }

    @Test
    void the_signed_in_user_reaches_the_enrichment_thread() throws Exception {
        // A manual job add is enrichment a signed-in user triggered; it must bill their key.
        UUID userId = UUID.randomUUID();
        signIn(userId);

        assertThat(userSeenOn(enrichmentExecutor)).contains(userId);
    }

    @Test
    void background_work_with_no_signed_in_user_still_sees_nobody() throws Exception {
        SecurityContextHolder.clearContext();

        assertThat(userSeenOn(userExecutor)).isEmpty();
        assertThat(userSeenOn(enrichmentExecutor)).isEmpty();
    }
}
