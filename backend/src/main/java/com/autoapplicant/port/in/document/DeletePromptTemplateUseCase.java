package com.autoapplicant.port.in.document;

import java.util.UUID;

public interface DeletePromptTemplateUseCase {
    /**
     * Deletes a template. System templates may only be deleted by admins;
     * user templates only by their owner.
     */
    void deleteTemplate(UUID templateId, UUID userId, boolean isAdmin);
}
