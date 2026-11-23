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
 * only the fallback for an install that never stored a choice, and is not written back. Whoever
 * settles also queues active postings counted as embedded but without a vector for the model.
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
        if (resolved == null) {
            resolved = settle();
            requeueMissingVectors(resolved);
        }
        return resolved;
    }

    /** The setup choice, stored when it differs; otherwise the stored one, then env. */
    private EmbeddingChoice settle() {
        Optional<EmbeddingChoice> current = stored.find();
        if (setupProvider == null || setupProvider.isBlank()) {
            return current.orElseGet(() -> choiceFor(fallbackProvider));
        }
        EmbeddingChoice chosen = choiceFor(setupProvider);
        if (!current.equals(Optional.of(chosen))) {
            stored.save(chosen);
            log.info("Embedding model is now {} ({}), was {}", chosen.model(), chosen.provider(),
                    current.map(EmbeddingChoice::model).orElse("unset"));
        }
        return chosen;
    }

    // Every process does this, not only the one that switched: a crawl that settled on the old
    // model before a switch, on a build without the write-time check, may have marked postings
    // embedded with old vectors only. Cheap when nothing needs it.
    private void requeueMissingVectors(EmbeddingChoice chosen) {
        int queued = jobs.requeueEmbeddingsForModel(chosen.model());
        if (queued > 0) log.info("{} postings queued for embedding with {}", queued, chosen.model());
    }

    private EmbeddingChoice choiceFor(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        String model = EmbeddingChoice.GEMINI.equals(normalized) ? geminiModel : openAiModel;
        return new EmbeddingChoice(normalized, model);
    }
}
