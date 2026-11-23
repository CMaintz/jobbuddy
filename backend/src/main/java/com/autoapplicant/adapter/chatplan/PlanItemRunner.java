package com.autoapplicant.adapter.chatplan;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.port.in.application.GetApplicationByIdUseCase;
import com.autoapplicant.port.in.application.UpdateApplicationDatesUseCase;
import com.autoapplicant.port.in.application.UpdateApplicationStatusUseCase;
import com.autoapplicant.port.in.job.IgnoreJobUseCase;
import com.autoapplicant.port.in.job.SaveJobUseCase;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Makes an approved change through the use case the app's own screens use for it, as her. It
 * passes on only the typed values the card showed, never text from the model.
 */
@Component
@Profile("desktop")
public class PlanItemRunner {

    private final SaveJobUseCase saveJob;
    private final IgnoreJobUseCase ignoreJob;
    private final UpdateApplicationStatusUseCase updateStatus;
    private final UpdateApplicationDatesUseCase updateDates;
    private final GetApplicationByIdUseCase applications;

    public PlanItemRunner(SaveJobUseCase saveJob, IgnoreJobUseCase ignoreJob,
                          UpdateApplicationStatusUseCase updateStatus,
                          UpdateApplicationDatesUseCase updateDates,
                          GetApplicationByIdUseCase applications) {
        this.saveJob = saveJob;
        this.ignoreJob = ignoreJob;
        this.updateStatus = updateStatus;
        this.updateDates = updateDates;
        this.applications = applications;
    }

    /**
     * @throws IllegalArgumentException the application is not there or not hers
     * @throws IllegalStateException the app refuses the change
     */
    void run(UUID userId, PlanItem item) {
        PlanItem.Details d = item.details();
        switch (item.kind()) {
            case SAVE_POSTING -> saveJob.saveJob(userId, d.postingId());
            case IGNORE_POSTING -> ignoreJob.ignoreJob(userId, d.postingId(), null);
            // Null notes: the app keeps hers, and the model has no say in them.
            case UPDATE_STATUS -> updateStatus.updateStatus(d.applicationId(), userId, d.toStatus(), null);
            case LOG_INTERVIEW -> logInterview(userId, d);
            case GENERATE_DOCUMENTS -> throw new UnsupportedOperationException(
                    "Documents are written in the background by DocumentQueue");
        }
    }

    /** The app replaces both dates at once, so her deadline is read now and written back. */
    private void logInterview(UUID userId, PlanItem.Details d) {
        Application app = applications.getApplicationById(d.applicationId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        updateDates.updateDates(app.id(), userId, app.deadline(), d.interviewAt());
    }
}
