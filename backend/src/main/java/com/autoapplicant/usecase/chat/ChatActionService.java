package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.port.in.chat.ResolveChatActionUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("desktop")
public class ChatActionService implements ResolveChatActionUseCase {

    private final ChatActions actions;

    public ChatActionService(JobRepositoryPort jobs) {
        this.actions = new ChatActions(jobs);
    }

    @Override
    public Optional<ChatAction> resolve(ChatAction.Kind kind, List<UUID> postingIds) {
        return actions.resolve(kind, postingIds);
    }
}
