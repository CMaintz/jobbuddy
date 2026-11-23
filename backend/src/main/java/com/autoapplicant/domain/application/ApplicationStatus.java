package com.autoapplicant.domain.application;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum ApplicationStatus {
    SAVED,
    PREPARING,
    APPLIED,
    RECRUITER_CONTACT,
    INTERVIEW,
    TECHNICAL_TEST,
    FINAL_ROUND,
    OFFER,
    REJECTED,
    ARCHIVED;

    /**
     * True when reaching this status means the employer did something, rather than the
     * candidate. Drives how a step reads on the progress timeline: their replies are the
     * news, the candidate's own moves are context.
     */
    public boolean isEmployerDriven() {
        return switch (this) {
            case RECRUITER_CONTACT, INTERVIEW, TECHNICAL_TEST, FINAL_ROUND, OFFER, REJECTED -> true;
            case SAVED, PREPARING, APPLIED, ARCHIVED -> false;
        };
    }

    public boolean canTransitionTo(ApplicationStatus next) {
        return switch (this) {
            // Straight to APPLIED as well: many people send the application the day they save
            // the posting, and recording that should not take two moves.
            case SAVED -> Set.of(PREPARING, APPLIED, ARCHIVED).contains(next);
            case PREPARING -> Set.of(APPLIED, SAVED, ARCHIVED).contains(next);
            case APPLIED -> Set.of(RECRUITER_CONTACT, REJECTED, ARCHIVED).contains(next);
            case RECRUITER_CONTACT -> Set.of(INTERVIEW, REJECTED, ARCHIVED).contains(next);
            case INTERVIEW -> Set.of(TECHNICAL_TEST, FINAL_ROUND, OFFER, REJECTED, ARCHIVED).contains(next);
            case TECHNICAL_TEST -> Set.of(FINAL_ROUND, REJECTED, ARCHIVED).contains(next);
            case FINAL_ROUND -> Set.of(OFFER, REJECTED, ARCHIVED).contains(next);
            case OFFER -> Set.of(ARCHIVED).contains(next);
            case REJECTED, ARCHIVED -> false;
        };
    }

    /** Every status this one may move to, so a client offers only moves the server accepts. */
    public List<ApplicationStatus> nextStatuses() {
        return Arrays.stream(values()).filter(this::canTransitionTo).toList();
    }
}
