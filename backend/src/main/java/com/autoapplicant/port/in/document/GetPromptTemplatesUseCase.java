package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.util.List;
import java.util.UUID;

public interface GetPromptTemplatesUseCase {
    List<PromptTemplate> getTemplates(UUID userId);
}
