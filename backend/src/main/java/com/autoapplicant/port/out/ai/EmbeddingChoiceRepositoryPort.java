package com.autoapplicant.port.out.ai;

import com.autoapplicant.domain.ai.EmbeddingChoice;
import java.util.Optional;

/** The one embedding provider and model the whole install embeds with, stored in the database. */
public interface EmbeddingChoiceRepositoryPort {

    /** Empty when nothing was ever stored, as on an install that predates the choice. */
    Optional<EmbeddingChoice> find();

    void save(EmbeddingChoice choice);
}
