package com.autoapplicant.port.out.interview;

import com.autoapplicant.domain.interview.InterviewQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewQuestionRepositoryPort {
    List<InterviewQuestion> findByJobIdAndUserId(UUID jobId, UUID userId);
    Optional<InterviewQuestion> findByIdAndUserId(UUID id, UUID userId);
    InterviewQuestion save(InterviewQuestion question);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
