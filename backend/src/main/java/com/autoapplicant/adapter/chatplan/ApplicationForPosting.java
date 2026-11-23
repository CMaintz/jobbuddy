package com.autoapplicant.adapter.chatplan;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.port.in.application.CreateApplicationUseCase;
import com.autoapplicant.port.in.application.GetApplicationsUseCase;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Her application for a posting, started as PREPARING if she has none, as quick apply starts
 * one. The app allows one per posting; one past PREPARING is refused when proposed, so the chat's
 * documents land beside her work in progress, never on one she has sent.
 */
@Component
@Profile("desktop")
public class ApplicationForPosting {

    private final GetApplicationsUseCase applications;
    private final CreateApplicationUseCase createApplication;

    public ApplicationForPosting(GetApplicationsUseCase applications,
                                 CreateApplicationUseCase createApplication) {
        this.applications = applications;
        this.createApplication = createApplication;
    }

    UUID find(UUID userId, UUID postingId) {
        return applications.getApplications(userId).stream()
                .filter(a -> postingId.equals(a.jobId()))
                .map(Application::id)
                .findFirst()
                .orElseGet(() -> createApplication.createApplication(new CreateApplicationCommand(
                        userId, postingId, null, null, null, ApplicationStatus.PREPARING, null,
                        null, null, null, null)).id());
    }
}
