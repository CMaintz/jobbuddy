package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.analytics.ResponseMetric;

import java.util.List;
import java.util.UUID;

public interface GetApplicationTimelineUseCase {
    List<ResponseMetric> getTimeline(UUID applicationId);
}
