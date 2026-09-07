package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.util.UUID;

public interface DuplicatePromptTemplateUseCase {
    PromptTemplate duplicate(UUID templateId, UUID userId, String newName);
}
