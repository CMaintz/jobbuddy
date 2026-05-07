package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromptTemplateRepositoryPort {
    PromptTemplate save(PromptTemplate template);
    Optional<PromptTemplate> findById(UUID id);
    List<PromptTemplate> findByUserId(UUID userId);
    List<PromptTemplate> findPublic();
}
