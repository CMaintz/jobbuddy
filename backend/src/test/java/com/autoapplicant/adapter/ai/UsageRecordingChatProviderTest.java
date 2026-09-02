package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.ai.AiUsageRecord;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.AiUsageRepositoryPort;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.ai.CurrentUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsageRecordingChatProviderTest {

    private static final PromptComposition PROMPT =
            new PromptComposition("sys", "tpl", "", "", "", "", "final");

    private ChatProviderPort delegate;
    private AiUsageRepositoryPort usageRepo;
    private CurrentUserPort currentUser;
    private UsageRecordingChatProvider provider;

    @BeforeEach
    void setUp() {
        delegate = mock(ChatProviderPort.class);
        usageRepo = mock(AiUsageRepositoryPort.class);
        currentUser = mock(CurrentUserPort.class);
        provider = new UsageRecordingChatProvider(delegate, usageRepo, currentUser);
    }

    @Test
    void a_generation_is_logged_against_the_user_who_asked_for_it() {
        UUID userId = UUID.randomUUID();
        when(currentUser.currentUserId()).thenReturn(Optional.of(userId));
        when(delegate.complete(any(), anyBoolean(), any()))
                .thenReturn(new AiCompletion("letter", 900, 300, "gpt-4o"));

        String text = provider.generateJson(PROMPT, "DOCUMENT_GENERATION");

        assertThat(text).isEqualTo("letter");
        ArgumentCaptor<AiUsageRecord> captor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(usageRepo).save(captor.capture());
        AiUsageRecord saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.tokensIn()).isEqualTo(900);
        assertThat(saved.tokensOut()).isEqualTo(300);
        assertThat(saved.model()).isEqualTo("gpt-4o");
        assertThat(saved.operation()).isEqualTo("DOCUMENT_GENERATION");
    }

    @Test
    void background_work_with_no_signed_in_user_is_not_logged() {
        when(currentUser.currentUserId()).thenReturn(Optional.empty());
        when(delegate.complete(any(), anyBoolean(), any()))
                .thenReturn(new AiCompletion("out", 10, 10, "gpt-4o"));

        provider.generate(PROMPT);

        verifyNoInteractions(usageRepo);
    }

    @Test
    void an_unlabelled_call_still_records_under_the_fallback_label() {
        when(currentUser.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(delegate.complete(any(), anyBoolean(), any()))
                .thenReturn(new AiCompletion("out", 5, 5, "gpt-4o"));

        provider.generate(PROMPT);

        ArgumentCaptor<AiUsageRecord> captor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(usageRepo).save(captor.capture());
        assertThat(captor.getValue().operation()).isEqualTo(ChatProviderPort.UNLABELLED_OPERATION);
    }

    @Test
    void a_failure_to_write_the_log_never_costs_the_caller_their_generation() {
        when(currentUser.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(delegate.complete(any(), anyBoolean(), any()))
                .thenReturn(new AiCompletion("still here", 1, 1, "gpt-4o"));
        when(usageRepo.save(any())).thenThrow(new RuntimeException("db down"));

        assertThat(provider.generate(PROMPT)).isEqualTo("still here");
    }

    @Test
    void an_over_long_operation_label_is_trimmed_to_the_column_width() {
        when(currentUser.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(delegate.complete(any(), anyBoolean(), any()))
                .thenReturn(new AiCompletion("out", 1, 1, "gpt-4o"));

        provider.generate(PROMPT, "X".repeat(120));

        ArgumentCaptor<AiUsageRecord> captor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(usageRepo).save(captor.capture());
        assertThat(captor.getValue().operation()).hasSize(50);
    }
}
