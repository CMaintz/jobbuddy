package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.InterviewStory;

import java.util.List;
import java.util.UUID;

public interface ManageStoryBankUseCase {
    List<InterviewStory> list(UUID userId);
    InterviewStory upsert(UUID userId, UUID id, String title, String situation, String task,
                          String action, String result, String reflection, List<String> tags);
    void remove(UUID userId, UUID id);
}
