package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.analytics.ResponseMetric;
import com.autoapplicant.port.in.application.GetApplicationTimelineUseCase;
import com.autoapplicant.port.out.analytics.ResponseMetricRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ApplicationTimelineService implements GetApplicationTimelineUseCase {

    private final ResponseMetricRepositoryPort responseMetricRepo;

    public ApplicationTimelineService(ResponseMetricRepositoryPort responseMetricRepo) {
        this.responseMetricRepo = responseMetricRepo;
    }

    @Override
    public List<ResponseMetric> getTimeline(UUID applicationId) {
        return responseMetricRepo.findByApplicationId(applicationId);
    }
}
