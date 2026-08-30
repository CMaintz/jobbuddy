package com.autoapplicant.domain.document;

/**
 * The kinds of generation a prompt template can drive.
 *
 * <p>These names are matched against {@link DocumentType} when resolving the default persona for
 * a generation, so a document type with no category here can never have a seeded persona — it
 * silently falls back to the built-in one in {@code PromptCompositionBuilder}. That is exactly
 * what happened to unsolicited applications and follow-ups until they were added.
 */
public enum PromptCategory {
    COVER_LETTER,
    APPLICATION,
    /** Speculative application to a company with no posted vacancy ("uopfordret ansøgning"). */
    UNSOLICITED_APPLICATION,
    RECRUITER_MESSAGE,
    /** The short nudge after an application or an unsolicited approach has gone quiet. */
    FOLLOW_UP_MESSAGE,
    CV_TAILORING,
    CV_ANALYSIS,
    GENERAL
}
