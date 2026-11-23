package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.in.ai.ResolveEmbeddingChoiceUseCase;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Whether this process has the key for the stored embedding provider. Without it every embed
 * call fails one by one and burns each posting's attempts, so the crawl and the desktop app check
 * once, up front, and stop with a message that names the provider and the variable to set.
 */
@Component
public class EmbeddingKeyGuard {

    /** The provider that has no key, and the environment variable that would supply it. */
    public record MissingKey(String provider, String envVar) {
    }

    private final ResolveEmbeddingChoiceUseCase choice;
    private final AppProperties props;

    public EmbeddingKeyGuard(ResolveEmbeddingChoiceUseCase choice, AppProperties props) {
        this.choice = choice;
        this.props = props;
    }

    public Optional<MissingKey> missingKey() {
        String provider = choice.currentChoice().provider();
        boolean gemini = EmbeddingChoice.GEMINI.equals(provider);
        String key = gemini ? props.getGemini().getApiKey() : props.getOpenai().getApiKey();
        if (key != null && !key.isBlank()) return Optional.empty();
        return Optional.of(new MissingKey(provider, gemini ? "GEMINI_API_KEY" : "OPENAI_API_KEY"));
    }

    /** @throws MissingEmbeddingKeyException naming the provider and its variable */
    public void requireKey() {
        missingKey().ifPresent(missing -> {
            throw new MissingEmbeddingKeyException(missing);
        });
    }
}
