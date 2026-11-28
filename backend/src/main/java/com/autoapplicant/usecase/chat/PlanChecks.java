package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.chat.PlanItem.Details;
import com.autoapplicant.domain.chat.ProposalRefusedException;
import com.autoapplicant.domain.chat.ProposedChange;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Checks a change against her data twice: when the model proposes it, to build what the card
 * promises from the database, and when she approves it, to find out whether that promise still
 * holds. Postings are shared by every user, so ownership is checked on her applications.
 */
final class PlanChecks {

    /**
     * How far from today an interview time may be. Wider than any real interview, and narrow
     * enough to refuse a year the app cannot show, such as +10000, which the model could be
     * steered into sending.
     */
    static final Period INTERVIEW_RANGE = Period.ofYears(1);

    private final JobRepositoryPort jobs;
    private final ApplicationRepositoryPort applications;
    private final Clock clock;
    private final DocumentChecks documents;

    PlanChecks(JobRepositoryPort jobs, ApplicationRepositoryPort applications, Clock clock) {
        this.jobs = jobs;
        this.applications = applications;
        this.clock = clock;
        this.documents = new DocumentChecks(applications);
    }

    /** What the card will say, from the database; refused when the change cannot happen. */
    Details details(UUID userId, ProposedChange change) {
        return switch (change.kind()) {
            case SAVE_POSTING, IGNORE_POSTING -> postingDetails(change.postingId());
            case GENERATE_DOCUMENTS -> documentDetails(userId, change.postingId());
            case UPDATE_STATUS -> statusDetails(userId, change);
            case LOG_INTERVIEW -> interviewDetails(userId, change);
        };
    }

    /** Why the approved item can no longer run as proposed, or empty when it can. */
    Optional<PlanFailure> staleness(UUID userId, PlanItem item) {
        return switch (item.details()) {
            case Details.Posting p -> jobs.findById(p.postingId()).isPresent()
                    ? Optional.empty() : Optional.of(PlanFailure.POSTING_GONE);
            case Details.Documents d -> jobs.findById(d.postingId())
                    .map(job -> documents.staleness(userId, job))
                    .orElse(Optional.of(PlanFailure.POSTING_GONE));
            case Details.StatusMove s -> applicationStaleness(userId, s.applicationId(),
                    app -> app.status() == s.fromStatus());
            case Details.Interview i -> applicationStaleness(userId, i.applicationId(),
                    app -> Objects.equals(app.interviewAt(), i.interviewBefore()));
        };
    }

    private Optional<PlanFailure> applicationStaleness(UUID userId, UUID applicationId,
                                                       Predicate<Application> unchanged) {
        Optional<Application> app = applications.findByIdAndUserId(applicationId, userId);
        if (app.isEmpty()) {
            return Optional.of(PlanFailure.APPLICATION_GONE);
        }
        return unchanged.test(app.get()) ? Optional.empty() : Optional.of(PlanFailure.CHANGED_SINCE);
    }

    private Details postingDetails(UUID postingId) {
        Job job = posting(postingId);
        return new Details.Posting(job.id(), job.title(), job.companyName());
    }

    private Details documentDetails(UUID userId, UUID postingId) {
        Job job = posting(postingId);
        documents.refusal(userId, job).ifPresent(why -> {
            throw new ProposalRefusedException(why);
        });
        return new Details.Documents(job.id(), job.title(), job.companyName());
    }

    private Job posting(UUID postingId) {
        return jobs.findById(require(postingId, "posting_id"))
                .orElseThrow(() -> new ProposalRefusedException(
                        "No such posting; use a posting id returned by the other tools"));
    }

    private Details statusDetails(UUID userId, ProposedChange change) {
        Application app = application(userId, change.applicationId());
        ApplicationStatus to = Optional.ofNullable(change.toStatus())
                .orElseThrow(() -> new ProposalRefusedException("status is required"));
        if (!app.status().canTransitionTo(to)) {
            throw new ProposalRefusedException("The application is " + app.status()
                    + " and can only move to " + app.status().nextStatuses());
        }
        Job job = posting(app);
        return new Details.StatusMove(job.id(), app.id(), job.title(), job.companyName(),
                app.status(), to);
    }

    private Details interviewDetails(UUID userId, ProposedChange change) {
        Application app = application(userId, change.applicationId());
        LocalDateTime at = change.interviewAt();
        LocalDate today = LocalDate.now(clock);
        if (at == null || at.toLocalDate().isBefore(today.minus(INTERVIEW_RANGE))
                || at.toLocalDate().isAfter(today.plus(INTERVIEW_RANGE))) {
            throw new ProposalRefusedException("interview_at must be a date within a year of today");
        }
        Job job = posting(app);
        return new Details.Interview(job.id(), app.id(), job.title(), job.companyName(),
                app.interviewAt(), change.interviewAt());
    }

    private Application application(UUID userId, UUID applicationId) {
        return applications.findByIdAndUserId(require(applicationId, "application_id"), userId)
                .orElseThrow(() -> new ProposalRefusedException(
                        "No such application; use an application id from list_applications"));
    }

    /** An application whose posting is gone leaves the card nothing to name. */
    private Job posting(Application app) {
        return Optional.ofNullable(app.jobId()).flatMap(jobs::findById)
                .orElseThrow(() -> new ProposalRefusedException(
                        "The posting of that application is gone; she can change it in the app"));
    }

    private static UUID require(UUID id, String name) {
        if (id == null) {
            throw new ProposalRefusedException(name + " is required");
        }
        return id;
    }
}
