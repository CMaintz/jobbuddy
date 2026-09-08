package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromptTemplateRepositoryPort {
    PromptTemplate save(PromptTemplate template);
    void deleteById(UUID id);
    Optional<PromptTemplate> findById(UUID id);
    List<PromptTemplate> findByUserId(UUID userId);
    List<PromptTemplate> findPublic();
    /** Returns the first system-default template for a given category, e.g. CV_TAILORING. */
    Optional<PromptTemplate> findSystemDefault(String category);

    /** Bumps the template's usage counter (called whenever it drives a generation). */
    void incrementUsage(UUID templateId);

    // ── Per-user favourites ──────────────────────────────────────
    java.util.Set<UUID> findFavouriteTemplateIds(UUID userId);
    void addFavourite(UUID userId, UUID templateId);
    void removeFavourite(UUID userId, UUID templateId);
}
