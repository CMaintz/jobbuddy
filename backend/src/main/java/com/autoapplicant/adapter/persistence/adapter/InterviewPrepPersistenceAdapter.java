package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.InterviewQuestionEntity;
import com.autoapplicant.adapter.persistence.repository.InterviewQuestionJpaRepository;
import com.autoapplicant.domain.interview.InterviewQuestion;
import com.autoapplicant.port.out.interview.InterviewQuestionRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class InterviewPrepPersistenceAdapter implements InterviewQuestionRepositoryPort {

    private final InterviewQuestionJpaRepository repo;

    public InterviewPrepPersistenceAdapter(InterviewQuestionJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<InterviewQuestion> findByJobIdAndUserId(UUID jobId, UUID userId) {
        return repo.findByJobIdAndUserIdOrderByDisplayOrderAsc(jobId, userId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<InterviewQuestion> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public InterviewQuestion save(InterviewQuestion q) {
        InterviewQuestionEntity e = q.id() != null
                ? repo.findById(q.id()).orElse(new InterviewQuestionEntity())
                : new InterviewQuestionEntity();
        e.setJobId(q.jobId());
        e.setUserId(q.userId());
        e.setQuestion(q.question());
        e.setCategory(q.category());
        e.setStarAnswer(q.starAnswer());
        e.setPracticed(q.practiced());
        e.setDisplayOrder(q.displayOrder());
        return toDomain(repo.save(e));
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private InterviewQuestion toDomain(InterviewQuestionEntity e) {
        return new InterviewQuestion(e.getId(), e.getJobId(), e.getUserId(),
                e.getQuestion(), e.getCategory(), e.getStarAnswer(),
                e.isPracticed(), e.getDisplayOrder(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
