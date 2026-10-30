package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.ai.AiCredential;
import com.autoapplicant.domain.ai.AiCredentialProvider;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.in.ai.ManageAiCredentialUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.ai.CurrentUserPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        props.getOpenai().setModel("openai-configured-model");
        props.getGemini().setModel("gemini-configured-model");
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

    @ParameterizedTest
    @CsvSource({"OPENAI, openai-configured-model", "GEMINI, gemini-configured-model"})
    void a_user_with_a_key_is_routed_to_the_adapter_for_their_provider(
            AiCredentialProvider keyProvider, String expectedModel) {
        // No model override on the key, so each adapter reports its own configured default:
        // that is what tells the OpenAI adapter from the Gemini one without a network call.
        when(currentUser.currentUserId()).thenReturn(Optional.of(USER));
        when(credentials.findCredential(USER)).thenReturn(Optional.of(new AiCredential(
                USER, keyProvider, "user-key", null, Instant.now())));

        assertThat(provider.chatModelName()).isEqualTo(expectedModel);
        verify(serverDefault, never()).chatModelName();
    }

    @Test
    void a_users_model_override_wins_over_the_configured_default() {
        when(currentUser.currentUserId()).thenReturn(Optional.of(USER));
        when(credentials.findCredential(USER)).thenReturn(Optional.of(new AiCredential(
                USER, AiCredentialProvider.OPENAI, "sk-user-key", "gpt-4o-mini", Instant.now())));

        assertThat(provider.chatModelName()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void a_key_that_cannot_be_read_falls_back_instead_of_failing_the_generation() {
        // A rotated encryption secret must not take generation down for everyone.
        when(currentUser.currentUserId()).thenReturn(Optional.of(USER));
        when(credentials.findCredential(USER)).thenThrow(new IllegalStateException("cannot decrypt"));

        assertThat(provider.generate(PROMPT)).isEqualTo("from the server's provider");
    }
}
