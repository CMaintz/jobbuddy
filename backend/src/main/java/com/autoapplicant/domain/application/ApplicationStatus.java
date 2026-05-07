package com.autoapplicant.domain.application;

import java.util.EnumSet;
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

    public boolean canTransitionTo(ApplicationStatus next) {
        return switch (this) {
            case SAVED -> Set.of(PREPARING, ARCHIVED).contains(next);
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
}
