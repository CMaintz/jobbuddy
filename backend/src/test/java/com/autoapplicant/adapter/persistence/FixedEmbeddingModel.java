package com.autoapplicant.adapter.persistence;

import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.in.ai.ResolveEmbeddingChoiceUseCase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** For persistence tests that embed with one model: the stored choice is OpenAI's. */
@TestConfiguration
public class FixedEmbeddingModel {

    public static final String MODEL = "text-embedding-3-small";

    @Bean
    ResolveEmbeddingChoiceUseCase fixedEmbeddingChoice() {
        return () -> new EmbeddingChoice(EmbeddingChoice.OPENAI, MODEL);
    }
}
