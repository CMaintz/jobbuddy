package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.util.List;

/**
 * Browsing the prompts other people have shared.
 *
 * <p>Sharing is opt-in and per-prompt: a prompt is listed here only when its author turned it
 * public. The app's own prompts never appear — everyone already has them, and listing them would
 * bury the handful someone actually wrote.
 */
public interface BrowsePublicPromptsUseCase {

    /** Shared prompts, newest first. Excludes the caller's own — they have those already. */
    List<PromptTemplate> browsePublic(java.util.UUID callerId);
}
