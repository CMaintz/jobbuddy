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
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final float[] VECTOR = {0.1f, 0.2f};
    private static final String MODEL = "text-embedding-3-small";

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

        updater().onProfileSaved(new ProfileSavedEvent(userId, profile("Backend engineer")));

        ArgumentCaptor<ProfileEmbedding> cap = ArgumentCaptor.forClass(ProfileEmbedding.class);
        verify(embeddingRepo).save(cap.capture());
        assertThat(cap.getValue().userId()).isEqualTo(userId);
        assertThat(cap.getValue().embedding()).isEqualTo(VECTOR);
        verify(aiProvider).embed("Backend engineer");
    }

    @Test
    void blank_profile_drops_the_stale_embedding_without_calling_the_provider() {
        updater().onProfileSaved(new ProfileSavedEvent(userId, profile(null)));

        verify(embeddingRepo).deleteByUserId(userId);
        verify(aiProvider, never()).embed(anyString());
    }

    @Test
    void provider_failure_is_swallowed() {
        when(aiProvider.embed(anyString())).thenThrow(new IllegalStateException("down"));

        updater().onProfileSaved(new ProfileSavedEvent(userId, profile("Backend engineer")));

        verify(embeddingRepo, never()).save(any());
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

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(AsyncTestConfig.class);
            ctx.registerBean(ProfileEmbeddingUpdater.class, this::updater);
            ctx.refresh();

            ctx.publishEvent(new ProfileSavedEvent(userId, profile("Backend engineer")));

            verify(embeddingRepo, timeout(ASYNC_WAIT_MS)).save(any());
            assertThat(embeddingThread.get()).isNotSameAs(Thread.currentThread());
        }
    }

    private ProfileEmbeddingUpdater updater() {
        return new ProfileEmbeddingUpdater(embeddingRepo, workExpRepo, projectRepo, certRepo,
                aiProvider, skillRepo);
    }

    private Profile profile(String headline) {
        return new Profile(UUID.randomUUID(), userId, headline, null, null,
                List.of(), List.of(), null, null, null, null, null, null, null);
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
