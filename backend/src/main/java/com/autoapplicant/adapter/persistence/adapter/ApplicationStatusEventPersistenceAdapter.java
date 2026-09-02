package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ApplicationStatusEventEntity;
import com.autoapplicant.adapter.persistence.repository.ApplicationStatusEventJpaRepository;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ApplicationStatusEventPersistenceAdapter implements ApplicationStatusEventRepositoryPort {

    private final ApplicationStatusEventJpaRepository repo;

    public ApplicationStatusEventPersistenceAdapter(ApplicationStatusEventJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public ApplicationStatusEvent save(ApplicationStatusEvent event) {
        ApplicationStatusEventEntity e = new ApplicationStatusEventEntity();
        e.setApplicationId(event.applicationId());
        e.setUserId(event.userId());
        e.setFromStatus(event.fromStatus() != null ? event.fromStatus().name() : null);
        e.setToStatus(event.toStatus().name());
        e.setOccurredAt(event.occurredAt());
        return toDomain(repo.save(e));
    }

    @Override
    public List<ApplicationStatusEvent> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByOccurredAtAsc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ApplicationStatusEvent> findByApplicationIdAndUserId(UUID applicationId, UUID userId) {
        return repo.findByApplicationIdAndUserIdOrderByOccurredAtAsc(applicationId, userId)
                .stream().map(this::toDomain).toList();
    }

    private ApplicationStatusEvent toDomain(ApplicationStatusEventEntity e) {
        return new ApplicationStatusEvent(e.getId(), e.getApplicationId(), e.getUserId(),
                parse(e.getFromStatus()), parse(e.getToStatus()), e.getOccurredAt());
    }

    private static ApplicationStatus parse(String v) {
        if (v == null) return null;
        try { return ApplicationStatus.valueOf(v); } catch (IllegalArgumentException ex) { return null; }
    }
}
