package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.analytics.ResponseMetric;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.domain.application.ApplicationTimelineEntry;
import com.autoapplicant.port.in.application.GetApplicationTimelineUseCase;
import com.autoapplicant.port.out.analytics.ResponseMetricRepositoryPort;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An application's progress, start to finish. The spine is the append-only status-event
 * ledger, so the user's own moves — saving a role, preparing it, sending it — appear
 * alongside the employer's replies; a timeline that only showed replies would be empty
 * for every application still waiting on one, which is most of them.
 *
 * <p>Response metrics carry the note written at the moment of a reply. Both rows are
 * written with the same instant inside one status change, which is what lets the note
 * find its event again.
 */
@Service
public class ApplicationTimelineService implements GetApplicationTimelineUseCase {

    private final ApplicationStatusEventRepositoryPort statusEventRepo;
    private final ResponseMetricRepositoryPort responseMetricRepo;

    public ApplicationTimelineService(ApplicationStatusEventRepositoryPort statusEventRepo,
                                      ResponseMetricRepositoryPort responseMetricRepo) {
        this.statusEventRepo = statusEventRepo;
        this.responseMetricRepo = responseMetricRepo;
    }

    @Override
    public List<ApplicationTimelineEntry> getTimeline(UUID applicationId, UUID userId) {
        Map<Instant, ResponseMetric> repliesByInstant = new HashMap<>();
        for (ResponseMetric metric : responseMetricRepo.findByApplicationIdAndUserId(applicationId, userId)) {
            repliesByInstant.put(metric.eventAt(), metric);
        }

        return statusEventRepo.findByApplicationIdAndUserId(applicationId, userId).stream()
                .map(event -> toEntry(event, repliesByInstant.get(event.occurredAt())))
                .toList();
    }

    private static ApplicationTimelineEntry toEntry(ApplicationStatusEvent event, ResponseMetric reply) {
        return new ApplicationTimelineEntry(
                event.occurredAt(),
                event.fromStatus(),
                event.toStatus(),
                reply != null,
                reply != null ? reply.notes() : null);
    }
}
