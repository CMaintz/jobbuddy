package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.in.ai.ResolveEmbeddingChoiceUseCase;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import java.util.List;
import java.util.function.Function;

/**
 * Embeds with the provider of the install's stored choice, built on first use. Lazy because the
 * choice is read from the database, and a context that never embeds should not need one.
 */
public class SelectedEmbeddingProvider implements EmbeddingProviderPort {

    private final ResolveEmbeddingChoiceUseCase choice;
    private final Function<EmbeddingChoice, EmbeddingProviderPort> factory;
    private EmbeddingProviderPort delegate;

    public SelectedEmbeddingProvider(ResolveEmbeddingChoiceUseCase choice,
                                     Function<EmbeddingChoice, EmbeddingProviderPort> factory) {
        this.choice = choice;
        this.factory = factory;
    }

    @Override
    public float[] embed(String text) {
        return delegate().embed(text);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        return delegate().embedAll(texts);
    }

    @Override
    public String embeddingModelName() {
        return delegate().embeddingModelName();
    }

    private synchronized EmbeddingProviderPort delegate() {
        if (delegate == null) delegate = factory.apply(choice.currentChoice());
        return delegate;
    }
}
