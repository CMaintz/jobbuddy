package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.AiCredential;
import com.autoapplicant.domain.ai.AiCredentialProvider;
import com.autoapplicant.port.out.ai.AiCredentialRepositoryPort;
import com.autoapplicant.port.out.security.SecretCipherPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A user's own API key must never be stored unencrypted, so a deployment without an encryption
 * key refuses the feature outright rather than degrading to plaintext.
 */
class AiCredentialServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AiCredentialRepositoryPort repo = mock(AiCredentialRepositoryPort.class);
    private final SecretCipherPort cipher = mock(SecretCipherPort.class);
    private final AiCredentialService service = new AiCredentialService(repo, cipher);

    @Test
    void aKeyIsNotStoredWhenNoEncryptionKeyIsConfigured() {
        when(cipher.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.setCredential(USER, AiCredentialProvider.OPENAI, "sk-abc", null))
                .isInstanceOf(IllegalStateException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void aBlankKeyIsRejected() {
        when(cipher.isConfigured()).thenReturn(true);

        assertThatThrownBy(() -> service.setCredential(USER, AiCredentialProvider.OPENAI, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void aProviderThatCannotBeBroughtIsRejected() {
        when(cipher.isConfigured()).thenReturn(true);
        AiCredentialProvider unknown = AiCredentialProvider.parse("claude-cli");

        assertThatThrownBy(() -> service.setCredential(USER, unknown, "sk-abc", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void aPastedKeyIsStoredTrimmedAndABlankModelAsNone() {
        // A key copied from a dashboard often carries a trailing newline, which the provider rejects.
        when(cipher.isConfigured()).thenReturn(true);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiCredential saved = service.setCredential(USER, AiCredentialProvider.GEMINI, " sk-abc\n", " ");

        assertThat(saved.apiKey()).isEqualTo("sk-abc");
        assertThat(saved.model()).isNull();
        assertThat(saved.provider()).isEqualTo(AiCredentialProvider.GEMINI);
    }

    @Test
    void aStoredKeyIsNotUsedOnceTheEncryptionKeyIsGone() {
        when(cipher.isConfigured()).thenReturn(false);
        when(repo.findByUserId(USER)).thenReturn(Optional.of(new AiCredential(
                USER, AiCredentialProvider.OPENAI, "sk-abc", null, Instant.EPOCH)));

        assertThat(service.findCredential(USER)).isEmpty();
    }
}
