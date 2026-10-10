package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.InterviewStory;
import java.util.List;
import java.util.UUID;

public interface InterviewStoryRepositoryPort {
    List<InterviewStory> findByUserId(UUID userId);

    /**
     * Creates the story when it has no id, otherwise updates the caller's existing story.
     * Throws {@link com.autoapplicant.domain.common.NotFoundException} when the id is unknown
     * or belongs to someone other than {@code story.userId()}.
     */
    InterviewStory save(InterviewStory story);

    void delete(UUID id, UUID userId);
}
