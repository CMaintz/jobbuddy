package com.autoapplicant.port.in.document;

import java.util.Set;
import java.util.UUID;

/** Per-user starring of prompt templates (own and system templates alike). */
public interface FavouritePromptTemplateUseCase {
    void favourite(UUID userId, UUID templateId);
    void unfavourite(UUID userId, UUID templateId);
    Set<UUID> getFavouriteIds(UUID userId);
}
