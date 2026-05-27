package com.autoapplicant.port.out.analytics;

import com.autoapplicant.domain.analytics.ResponseMetric;

import java.util.List;
import java.util.UUID;

public interface ResponseMetricRepositoryPort {

    ResponseMetric save(ResponseMetric metric);

    List<ResponseMetric> findByApplicationId(UUID applicationId);
}
