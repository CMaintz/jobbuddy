package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ResponseMetricEntity;
import com.autoapplicant.adapter.persistence.repository.ResponseMetricJpaRepository;
import com.autoapplicant.domain.analytics.ResponseMetric;
import com.autoapplicant.port.out.analytics.ResponseMetricRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ResponseMetricPersistenceAdapter implements ResponseMetricRepositoryPort {

    private final ResponseMetricJpaRepository repo;

    public ResponseMetricPersistenceAdapter(ResponseMetricJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public ResponseMetric save(ResponseMetric metric) {
        ResponseMetricEntity entity = toEntity(metric);
        return toDomain(repo.save(entity));
    }

    @Override
    public List<ResponseMetric> findByApplicationId(UUID applicationId) {
        return repo.findByApplicationIdOrderByEventAtAsc(applicationId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private ResponseMetricEntity toEntity(ResponseMetric metric) {
        ResponseMetricEntity e = new ResponseMetricEntity();
        e.setUserId(metric.userId());
        e.setJobId(metric.jobId());
        e.setApplicationId(metric.applicationId());
        e.setEventType(metric.eventType());
        e.setEventAt(metric.eventAt());
        e.setNotes(metric.notes());
        return e;
    }

    private ResponseMetric toDomain(ResponseMetricEntity e) {
        return new ResponseMetric(e.getId(), e.getUserId(), e.getJobId(),
                e.getApplicationId(), e.getEventType(), e.getEventAt(), e.getNotes());
    }
}
