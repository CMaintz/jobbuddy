package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.util.UUID;

public interface CreatePromptTemplateUseCase {
    PromptTemplate createTemplate(UUID userId, PromptTemplate template);
}
