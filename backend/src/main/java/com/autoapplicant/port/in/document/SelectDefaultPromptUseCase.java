package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.PromptCategory;
import com.autoapplicant.domain.document.PromptTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Choosing which prompt a category uses by default.
 *
 * <p>The app seeds a default per category, and the user can point the category at another
 * template instead — their own, or another of the app's. The choice is theirs alone: it never
 * edits the seeded template, which stays protected and available to fall back to.
 */
public interface SelectDefaultPromptUseCase {

    /** The template this user gets for the category when they name none. */
    Optional<PromptTemplate> getDefault(UUID userId, PromptCategory category);

    /** Points the category at this template for this user. */
    void selectDefault(UUID userId, PromptCategory category, UUID templateId);

    /** Drops the user's choice, restoring the app's seeded default. */
    void resetDefault(UUID userId, PromptCategory category);
}
