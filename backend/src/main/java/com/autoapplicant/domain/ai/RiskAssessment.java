package com.autoapplicant.domain.ai;

import java.util.List;

/**
 * Posting/employer risk assessment — deliberately SEPARATE from the fit score. Legitimacy
 * (ghost-job signals), individual risk signals, and compensation reliability are reported
 * so the user can judge whether a role is real and worth their time, but they never adjust
 * the 0-100 fit score or its dimensions. Present only for job-targeted analyses.
 *
 * <p>Framing borrowed from an external reference implementation: surface signals, never accuse; note legitimate
 * explanations; leave the decision to the human. "Not assessed" is a first-class value.
 */
public record RiskAssessment(
        Legitimacy legitimacy,
        String legitimacyNote,
        List<RiskSignal> signals,
        CompensationReliability compensationReliability,
        String compensationNote
) {
    /** One observed risk indicator (e.g. stale posting, vague requirements, comp all-variable). */
    public record RiskSignal(String label, String severity, String note) {}

    /** How confident we are the posting is a real, active opening. */
    public enum Legitimacy { HIGH_CONFIDENCE, CAUTION, SUSPICIOUS, NOT_ASSESSED }

    /** How much the advertised compensation can be trusted as real base pay. */
    public enum CompensationReliability { HIGH, MEDIUM, LOW, UNKNOWN }
}
