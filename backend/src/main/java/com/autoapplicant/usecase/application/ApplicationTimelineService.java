package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.domain.application.ApplicationTimelineEntry;
import com.autoapplicant.port.in.application.GetApplicationTimelineUseCase;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * An application's progress, start to finish, read straight off the append-only
 * status-event ledger. The candidate's own moves — saving a role, preparing it,
 * sending it — appear alongside the employer's replies; a timeline that showed only
 * replies would be empty for every application still waiting on one, which is most
 * of them.
 */
@Service
public class ApplicationTimelineService implements GetApplicationTimelineUseCase {

    private final ApplicationStatusEventRepositoryPort statusEventRepo;

    public ApplicationTimelineService(ApplicationStatusEventRepositoryPort statusEventRepo) {
        this.statusEventRepo = statusEventRepo;
    }

    @Override
    public List<ApplicationTimelineEntry> getTimeline(UUID applicationId, UUID userId) {
        return statusEventRepo.findByApplicationIdAndUserId(applicationId, userId).stream()
                .map(ApplicationTimelineService::toEntry)
                .toList();
    }

    private static ApplicationTimelineEntry toEntry(ApplicationStatusEvent event) {
        return new ApplicationTimelineEntry(
                event.occurredAt(),
                event.fromStatus(),
                event.toStatus(),
                event.toStatus().isEmployerDriven(),
                event.notes());
    }
}
