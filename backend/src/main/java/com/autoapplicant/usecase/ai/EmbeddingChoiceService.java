package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.in.ai.ResolveEmbeddingChoiceUseCase;
import com.autoapplicant.port.out.ai.EmbeddingChoiceRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles which embedding provider the process uses, once.
 *
 * <p>The desktop app is where she picks it (setup passes it in as the setup provider): that
 * choice is stored, and a change queues every posting without a vector for the new model. Every
 * other process (the scheduled crawl) follows what is stored, whatever its own env says. Env is
 * only the fallback for an install that never stored a choice, and is not written back.
 */
@Service
public class EmbeddingChoiceService implements ResolveEmbeddingChoiceUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingChoiceService.class);

    private final EmbeddingChoiceRepositoryPort stored;
    private final JobRepositoryPort jobs;
    private final String setupProvider;
    private final String fallbackProvider;
    private final String openAiModel;
    private final String geminiModel;
    private EmbeddingChoice resolved;

    public EmbeddingChoiceService(EmbeddingChoiceRepositoryPort stored, JobRepositoryPort jobs,
                                  @Value("${app.embedding.setup-provider:}") String setupProvider,
                                  @Value("${app.ai.enrichment-provider:openai}") String fallbackProvider,
                                  @Value("${app.openai.embedding-model}") String openAiModel,
                                  @Value("${app.gemini.embedding-model}") String geminiModel) {
        this.stored = stored;
        this.jobs = jobs;
        this.setupProvider = setupProvider;
        this.fallbackProvider = fallbackProvider;
        this.openAiModel = openAiModel;
        this.geminiModel = geminiModel;
    }

    @Override
    @Transactional
    public synchronized EmbeddingChoice currentChoice() {
        if (resolved == null) resolved = resolve();
        return resolved;
    }

    private EmbeddingChoice resolve() {
        Optional<EmbeddingChoice> current = stored.find();
        if (setupProvider == null || setupProvider.isBlank()) {
            return current.orElseGet(() -> choiceFor(fallbackProvider));
        }
        EmbeddingChoice chosen = choiceFor(setupProvider);
        if (!current.equals(Optional.of(chosen))) {
            stored.save(chosen);
            int queued = jobs.requeueEmbeddingsForModel(chosen.model());
            log.info("Embedding model is now {} ({}), was {}; {} postings queued for it",
                    chosen.model(), chosen.provider(), current.map(EmbeddingChoice::model).orElse("unset"),
                    queued);
        }
        return chosen;
    }

    private EmbeddingChoice choiceFor(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        String model = EmbeddingChoice.GEMINI.equals(normalized) ? geminiModel : openAiModel;
        return new EmbeddingChoice(normalized, model);
    }
}
