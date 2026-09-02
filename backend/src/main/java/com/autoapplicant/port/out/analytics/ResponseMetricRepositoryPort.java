package com.autoapplicant.port.out.analytics;

import com.autoapplicant.domain.analytics.ResponseMetric;

import java.util.List;
import java.util.UUID;

public interface ResponseMetricRepositoryPort {

    ResponseMetric save(ResponseMetric metric);

    /** Scoped by user: an application id alone must not open another user's timeline. */
    List<ResponseMetric> findByApplicationIdAndUserId(UUID applicationId, UUID userId);
}
