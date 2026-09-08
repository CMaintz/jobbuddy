package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.InterviewStory;
import com.autoapplicant.port.in.user.ManageStoryBankUseCase;
import com.autoapplicant.port.out.user.InterviewStoryRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class StoryBankService implements ManageStoryBankUseCase {

    private final InterviewStoryRepositoryPort repo;

    public StoryBankService(InterviewStoryRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<InterviewStory> list(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public InterviewStory upsert(UUID userId, UUID id, String title, String situation, String task,
                                 String action, String result, String reflection, List<String> tags) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Story title must not be blank");
        }
        return repo.save(new InterviewStory(id, userId, title.trim(), situation, task, action,
                result, reflection, tags != null ? tags : List.of(), null, null));
    }

    @Override
    public void remove(UUID userId, UUID id) {
        repo.delete(id, userId);
    }
}
