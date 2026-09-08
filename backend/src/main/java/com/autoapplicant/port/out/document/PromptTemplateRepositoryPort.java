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
    /**
     * Prompts other people have chosen to share: public, and not app-origin. The app's own
     * prompts are public in the sense that everyone already has them, so listing them here
     * would bury the handful a person actually wrote behind the two dozen that ship.
     */
    List<PromptTemplate> findPublic();
    /** The app's seeded starting point for a category, e.g. CV_TAILORING. */
    Optional<PromptTemplate> findSystemDefault(String category);

    /**
     * The template this user should get for the category when they name none: their own chosen
     * default if they have set one, otherwise the app's seeded default.
     *
     * <p>Kept separate from {@link #findSystemDefault(String)} because a user switching their
     * default must not write to app-owned content.
     */
    Optional<PromptTemplate> findDefaultFor(UUID userId, String category);

    /** Records this user's choice of default for a category. */
    void setUserDefault(UUID userId, String category, UUID templateId);

    /** Drops the user's override so the category falls back to the app's default. */
    void clearUserDefault(UUID userId, String category);

    /** Bumps the template's usage counter (called whenever it drives a generation). */
    void incrementUsage(UUID templateId);

    // ── Per-user favourites ──────────────────────────────────────
    java.util.Set<UUID> findFavouriteTemplateIds(UUID userId);
    void addFavourite(UUID userId, UUID templateId);
    void removeFavourite(UUID userId, UUID templateId);
}
