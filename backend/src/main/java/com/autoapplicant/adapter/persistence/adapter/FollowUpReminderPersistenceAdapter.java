package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.FollowUpReminderEntity;
import com.autoapplicant.adapter.persistence.repository.FollowUpReminderJpaRepository;
import com.autoapplicant.domain.reminder.FollowUpReminder;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class FollowUpReminderPersistenceAdapter implements FollowUpReminderRepositoryPort {

    private final FollowUpReminderJpaRepository repo;

    public FollowUpReminderPersistenceAdapter(FollowUpReminderJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<FollowUpReminder> findByApplicationIdAndUserId(UUID applicationId, UUID userId) {
        return repo.findByApplicationIdAndUserIdOrderByDueAtAsc(applicationId, userId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<FollowUpReminder> findDueByUserId(UUID userId, Instant upTo) {
        return repo.findDueByUserId(userId, upTo)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<FollowUpReminder> findOpenByUserId(UUID userId) {
        return repo.findByUserIdAndCompletedFalseOrderByDueAtAsc(userId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<FollowUpReminder> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public FollowUpReminder save(FollowUpReminder r) {
        FollowUpReminderEntity e = r.id() != null
                ? repo.findById(r.id()).orElse(new FollowUpReminderEntity())
                : new FollowUpReminderEntity();
        e.setApplicationId(r.applicationId());
        e.setUserId(r.userId());
        e.setNote(r.note());
        e.setDueAt(r.dueAt());
        e.setCompleted(r.completed());
        e.setCompletedAt(r.completedAt());
        return toDomain(repo.save(e));
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private FollowUpReminder toDomain(FollowUpReminderEntity e) {
        return new FollowUpReminder(e.getId(), e.getApplicationId(), e.getUserId(),
                e.getNote(), e.getDueAt(), e.isCompleted(), e.getCompletedAt(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
