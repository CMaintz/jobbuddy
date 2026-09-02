package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.ApplicationTimelineEntry;

import java.util.List;
import java.util.UUID;

public interface GetApplicationTimelineUseCase {
    List<ApplicationTimelineEntry> getTimeline(UUID applicationId, UUID userId);
}
