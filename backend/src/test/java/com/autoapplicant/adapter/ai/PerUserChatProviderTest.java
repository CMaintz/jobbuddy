package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.ai.AiCredential;
import com.autoapplicant.domain.ai.AiCredentialProvider;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.in.ai.ManageAiCredentialUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.ai.CurrentUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class PerUserChatProviderTest {

    private static final PromptComposition PROMPT =
            new PromptComposition("sys", "tpl", "", "", "", "", "final");
    private static final UUID USER = UUID.randomUUID();

    private ChatProviderPort serverDefault;
    private ManageAiCredentialUseCase credentials;
    private CurrentUserPort currentUser;
    private PerUserChatProvider provider;

    @BeforeEach
    void setUp() {
        serverDefault = mock(ChatProviderPort.class);
        credentials = mock(ManageAiCredentialUseCase.class);
        currentUser = mock(CurrentUserPort.class);
        AppProperties props = new AppProperties();
        provider = new PerUserChatProvider(serverDefault, credentials, currentUser, props);
        lenient().when(serverDefault.complete(any(), anyBoolean(), any()))
                .thenReturn(new AiCompletion("from the server's provider", 1, 1, "cli-agent"));
    }

    @Test
    void a_user_with_no_key_of_their_own_still_gets_the_servers_provider() {
        // On a personal install that provider is the local CLI agent, so the
        // flat-fee terminal path keeps working exactly as before.
        when(currentUser.currentUserId()).thenReturn(Optional.of(USER));
        when(credentials.findCredential(USER)).thenReturn(Optional.empty());

        assertThat(provider.generate(PROMPT)).isEqualTo("from the server's provider");
        verify(serverDefault).complete(any(), anyBoolean(), any());
    }

    @Test
    void background_work_with_no_user_uses_the_servers_provider() {
        when(currentUser.currentUserId()).thenReturn(Optional.empty());

        assertThat(provider.generate(PROMPT)).isEqualTo("from the server's provider");
        verifyNoInteractions(credentials);
    }

    @Test
    void a_user_with_a_key_is_routed_away_from_the_servers_provider() {
        when(currentUser.currentUserId()).thenReturn(Optional.of(USER));
        when(credentials.findCredential(USER)).thenReturn(Optional.of(new AiCredential(
                USER, AiCredentialProvider.OPENAI, "sk-user-key", "gpt-4o", Instant.now())));

        // The adapter is built against their key; the model name comes from their
        // override rather than the server's configuration.
        assertThat(provider.chatModelName()).isEqualTo("gpt-4o");
        verify(serverDefault, never()).chatModelName();
    }

    @Test
    void a_key_that_cannot_be_read_falls_back_instead_of_failing_the_generation() {
        // A rotated encryption secret must not take generation down for everyone.
        when(currentUser.currentUserId()).thenReturn(Optional.of(USER));
        when(credentials.findCredential(USER)).thenThrow(new IllegalStateException("cannot decrypt"));

        assertThat(provider.generate(PROMPT)).isEqualTo("from the server's provider");
    }
}
