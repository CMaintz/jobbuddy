package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * When a CV and letter may be written for a posting, mirroring what quick apply lets her start
 * from: a posting with enough text to tailor to and for the fact gate to check the letter
 * against, and no application she has already moved past preparing.
 */
final class DocumentChecks {

    /** Quick apply's own floor for a usable posting text. */
    static final int MIN_POSTING_TEXT = 40;

    private static final Set<ApplicationStatus> OPEN = Set.of(
            ApplicationStatus.SAVED, ApplicationStatus.PREPARING);

    private final ApplicationRepositoryPort applications;

    DocumentChecks(ApplicationRepositoryPort applications) {
        this.applications = applications;
    }

    /** Why documents cannot be written for the posting now, for the model; empty when they can. */
    Optional<String> refusal(UUID userId, Job job) {
        if (!hasText(job)) {
            return Optional.of("That posting has no text yet to tailor to; try again later");
        }
        return sentApplication(userId, job.id()).map(app -> "She has already moved her"
                + " application for that posting to " + app.status() + "; new documents are only"
                + " written for one she is still preparing");
    }

    /** The same checks at approval, as a failure for the card. */
    Optional<PlanFailure> staleness(UUID userId, Job job) {
        if (!hasText(job)) {
            return Optional.of(PlanFailure.NOT_ALLOWED);
        }
        return sentApplication(userId, job.id()).map(app -> PlanFailure.CHANGED_SINCE);
    }

    private static boolean hasText(Job job) {
        return job.descriptionClean() != null
                && job.descriptionClean().strip().length() > MIN_POSTING_TEXT;
    }

    private Optional<Application> sentApplication(UUID userId, UUID postingId) {
        return applications.findByUserId(userId).stream()
                .filter(a -> postingId.equals(a.jobId()) && !OPEN.contains(a.status()))
                .findFirst();
    }
}
