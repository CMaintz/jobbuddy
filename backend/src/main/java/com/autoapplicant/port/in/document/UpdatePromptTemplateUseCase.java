package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.util.UUID;

public interface UpdatePromptTemplateUseCase {
    /**
     * Updates a template's content. System templates may only be changed by admins;
     * user templates only by their owner.
     */
    PromptTemplate updateTemplate(UUID templateId, UUID userId, boolean isAdmin, PromptTemplate changes);
}
