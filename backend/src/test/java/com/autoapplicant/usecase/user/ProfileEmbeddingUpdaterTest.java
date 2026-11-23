package com.autoapplicant.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfileEmbedding;
import com.autoapplicant.domain.user.ProfileSavedEvent;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.event.TransactionalEventListenerFactory;

class ProfileEmbeddingUpdaterTest {

    private static final long ASYNC_WAIT_MS = 5_000;
    private static final int BURST = 5;
    private static final float[] VECTOR = {0.1f, 0.2f};
    private static final String MODEL = "text-embedding-3-small";

    private final ProfileRepositoryPort profileRepo = mock(ProfileRepositoryPort.class);
    private final ProfileEmbeddingRepositoryPort embeddingRepo = mock(ProfileEmbeddingRepositoryPort.class);
    private final WorkExperienceRepositoryPort workExpRepo = mock(WorkExperienceRepositoryPort.class);
    private final ProjectRepositoryPort projectRepo = mock(ProjectRepositoryPort.class);
    private final CertificationRepositoryPort certRepo = mock(CertificationRepositoryPort.class);
    private final AiProviderPort aiProvider = mock(AiProviderPort.class);
    private final ProfileSkillRepositoryPort skillRepo = mock(ProfileSkillRepositoryPort.class);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(workExpRepo.findByUserId(userId)).thenReturn(List.of());
        when(projectRepo.findByUserId(userId)).thenReturn(List.of());
        when(certRepo.findByUserId(userId)).thenReturn(List.of());
        when(skillRepo.findByUserId(userId)).thenReturn(List.of());
    }

    @Test
    void saved_profile_is_embedded_and_stored() {
        when(aiProvider.embed(anyString())).thenReturn(VECTOR);
        when(aiProvider.embeddingModelName()).thenReturn(MODEL);

        storedProfile("Backend engineer");

        updater().onProfileSaved(new ProfileSavedEvent(userId));

        ArgumentCaptor<ProfileEmbedding> cap = ArgumentCaptor.forClass(ProfileEmbedding.class);
        verify(embeddingRepo).save(cap.capture());
        assertThat(cap.getValue().userId()).isEqualTo(userId);
        assertThat(cap.getValue().embedding()).isEqualTo(VECTOR);
        verify(aiProvider).embed("Backend engineer");
    }

    @Test
    void blank_profile_drops_the_stale_embedding_without_calling_the_provider() {
        storedProfile(null);

        updater().onProfileSaved(new ProfileSavedEvent(userId));

        verify(embeddingRepo).deleteByUserId(userId);
        verify(aiProvider, never()).embed(anyString());
    }

    @Test
    void provider_failure_is_swallowed() {
        when(aiProvider.embed(anyString())).thenThrow(new IllegalStateException("down"));
        storedProfile("Backend engineer");

        updater().onProfileSaved(new ProfileSavedEvent(userId));

        verify(embeddingRepo, never()).save(any());
    }

    @Test
    void missing_profile_drops_the_embedding() {
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.empty());

        updater().onProfileSaved(new ProfileSavedEvent(userId));

        verify(embeddingRepo).deleteByUserId(userId);
        verify(aiProvider, never()).embed(anyString());
    }

    /** Overlapping refreshes for one user could store an older vector over a newer one. */
    @Test
    void refreshes_for_one_user_never_overlap() throws Exception {
        storedProfile("Backend engineer");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        when(aiProvider.embed(anyString())).thenAnswer(inv -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            if (started.incrementAndGet() == 1) {
                firstStarted.countDown();
                assertThat(releaseFirst.await(ASYNC_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
            }
            inFlight.decrementAndGet();
            return VECTOR;
        });
        when(aiProvider.embeddingModelName()).thenReturn(MODEL);
        ProfileEmbeddingUpdater updater = updater();

        Thread first = saveOnNewThread(updater);
        assertThat(firstStarted.await(ASYNC_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        Thread second = saveOnNewThread(updater);
        // Without the lock the second refresh reaches embed instead of parking, so stop on either.
        awaitUntil(() -> second.getState() == Thread.State.WAITING || started.get() > 1);
        assertThat(started.get()).isEqualTo(1);
        releaseFirst.countDown();
        joinAll(first, second);

        assertThat(maxInFlight.get()).isEqualTo(1);
    }

    /** A burst of saves must not turn into one embed call each. */
    @Test
    void saves_arriving_while_a_refresh_waits_are_coalesced() throws Exception {
        storedProfile("Backend engineer");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(aiProvider.embed(anyString())).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                assertThat(releaseFirst.await(ASYNC_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
            }
            return VECTOR;
        });
        when(aiProvider.embeddingModelName()).thenReturn(MODEL);
        ProfileEmbeddingUpdater updater = updater();

        Thread running = saveOnNewThread(updater);
        assertThat(firstStarted.await(ASYNC_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        Thread queued = saveOnNewThread(updater);
        // The queued save registers as waiting before it parks on the user lock, so once it is
        // parked every later save must see it and be dropped.
        awaitUntil(() -> queued.getState() == Thread.State.WAITING);
        for (int i = 0; i < BURST; i++) {
            updater.onProfileSaved(new ProfileSavedEvent(userId));
        }
        releaseFirst.countDown();
        joinAll(running, queued);

        assertThat(calls.get()).isEqualTo(2);
    }

    /** The regression: publishing the event must run the embedding on the async executor. */
    @Test
    void published_event_is_handled_off_the_publishing_thread() {
        AtomicReference<Thread> embeddingThread = new AtomicReference<>();
        when(aiProvider.embed(anyString())).thenAnswer(inv -> {
            embeddingThread.set(Thread.currentThread());
            return VECTOR;
        });
        when(aiProvider.embeddingModelName()).thenReturn(MODEL);
        storedProfile("Backend engineer");

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(AsyncTestConfig.class);
            ctx.registerBean(ProfileEmbeddingUpdater.class, this::updater);
            ctx.refresh();

            ctx.publishEvent(new ProfileSavedEvent(userId));

            verify(embeddingRepo, timeout(ASYNC_WAIT_MS)).save(any());
            assertThat(embeddingThread.get()).isNotSameAs(Thread.currentThread());
        }
    }

    // A dedicated thread, not a pool worker: an idle pool worker is also WAITING, so its state
    // could not tell "parked on the user lock" apart from "not started yet".
    private Thread saveOnNewThread(ProfileEmbeddingUpdater updater) {
        Thread thread = new Thread(() -> updater.onProfileSaved(new ProfileSavedEvent(userId)));
        thread.start();
        return thread;
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ASYNC_WAIT_MS);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition not met in time").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }

    private static void joinAll(Thread... threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join(ASYNC_WAIT_MS);
            assertThat(thread.isAlive()).as("save still running").isFalse();
        }
    }

    private ProfileEmbeddingUpdater updater() {
        return new ProfileEmbeddingUpdater(profileRepo, embeddingRepo, workExpRepo, projectRepo, certRepo,
                aiProvider, skillRepo);
    }

    private void storedProfile(String headline) {
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(new Profile(UUID.randomUUID(),
                userId, headline, null, null, List.of(), List.of(), null, null, null, null, null,
                null, null)));
    }

    @Configuration
    @EnableAsync
    static class AsyncTestConfig {

        @Bean(destroyMethod = "shutdown")
        ExecutorService userAiTaskExecutorService() {
            return Executors.newSingleThreadExecutor();
        }

        @Bean(name = "userAiTaskExecutor")
        Executor userAiTaskExecutor(ExecutorService userAiTaskExecutorService) {
            return userAiTaskExecutorService;
        }

        @Bean
        static TransactionalEventListenerFactory transactionalEventListenerFactory() {
            return new TransactionalEventListenerFactory();
        }
    }
}
