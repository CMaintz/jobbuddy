package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.InterviewStory;

import java.util.List;
import java.util.UUID;

public interface InterviewStoryRepositoryPort {
    List<InterviewStory> findByUserId(UUID userId);
    InterviewStory save(InterviewStory story);
    void delete(UUID id, UUID userId);
}
