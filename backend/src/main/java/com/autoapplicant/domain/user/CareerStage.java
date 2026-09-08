package com.autoapplicant.domain.user;

/**
 * The candidate's self-declared career stage. Identity-free and safe to send to the AI — it
 * drives stage-appropriate framing (a new grad leads with education/projects and never claims
 * years they don't have; a senior leads with impact and scope) and the default CV section order.
 *
 * <p>Distinct from job-side {@link com.autoapplicant.domain.job.Seniority}: that classifies a
 * posting, this classifies the person.
 */
public enum CareerStage {
    /** Still studying — no degree yet. */
    STUDENT,
    /** Graduated within ~1 year; little or no full-time professional experience. */
    NEW_GRAD,
    /** Roughly 1–3 years of professional experience. */
    EARLY_CAREER,
    /** Roughly 3–8 years; the default when unset for an experienced candidate. */
    MID_CAREER,
    /** 8+ years of deep individual-contributor experience. */
    SENIOR,
    /** Leadership / management / staff+ scope. */
    LEAD,
    /** Transitioning from another field; transferable-skills framing matters most. */
    CAREER_CHANGER;

    /** True for stages where education and projects should generally precede work experience. */
    public boolean isEarlyStage() {
        return this == STUDENT || this == NEW_GRAD;
    }
}
