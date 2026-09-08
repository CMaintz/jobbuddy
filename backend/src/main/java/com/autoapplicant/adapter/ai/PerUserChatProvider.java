package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.ai.AiCredential;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.in.ai.ManageAiCredentialUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.ai.CurrentUserPort;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Sends a user's generations through their own API key when they have supplied one,
 * and through whatever the server is configured with when they have not.
 *
 * <p>The fallback is the point: on a personal install the server default is the local
 * CLI agent, so running with no key stored keeps the flat-fee terminal path exactly as
 * it was. On a hosted install the same code lets each user bring their own key and pay
 * for their own generations.
 */
public class PerUserChatProvider implements ChatProviderPort {

    private static final Logger log = LoggerFactory.getLogger(PerUserChatProvider.class);

    private final ChatProviderPort serverDefault;
    private final ManageAiCredentialUseCase credentials;
    private final CurrentUserPort currentUser;
    private final AppProperties props;

    public PerUserChatProvider(ChatProviderPort serverDefault,
                               ManageAiCredentialUseCase credentials,
                               CurrentUserPort currentUser,
                               AppProperties props) {
        this.serverDefault = serverDefault;
        this.credentials = credentials;
        this.currentUser = currentUser;
        this.props = props;
    }

    @Override
    public AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation) {
        return providerForCurrentUser().complete(composition, jsonObject, operation);
    }

    @Override
    public String chatModelName() {
        return providerForCurrentUser().chatModelName();
    }

    private ChatProviderPort providerForCurrentUser() {
        return currentUser.currentUserId()
                .flatMap(this::credentialFor)
                .<ChatProviderPort>map(this::adapterFor)
                .orElse(serverDefault);
    }

    private Optional<AiCredential> credentialFor(java.util.UUID userId) {
        try {
            return credentials.findCredential(userId);
        } catch (RuntimeException e) {
            // A key we cannot read (rotated encryption secret, corrupt row) must not
            // take generation down — fall back to the server's own provider.
            log.warn("Could not read the stored AI key for user {}, using the server default: {}",
                    userId, e.toString());
            return Optional.empty();
        }
    }

    private ChatProviderPort adapterFor(AiCredential credential) {
        return switch (credential.provider()) {
            case OPENAI -> new OpenAiAdapter(clientFor(credential.apiKey(), null), props, credential.model());
            case GEMINI -> new GeminiAdapter(
                    clientFor(credential.apiKey(), AiConfig.GEMINI_BASE_URL), props, credential.model());
        };
    }

    private static OpenAIClient clientFor(String apiKey, String baseUrl) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(60));
        if (baseUrl != null) builder.baseUrl(baseUrl);
        return builder.build();
    }
}
