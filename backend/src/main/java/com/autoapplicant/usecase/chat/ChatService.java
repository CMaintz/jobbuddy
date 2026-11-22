package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.ChatTranscript;
import com.autoapplicant.port.in.chat.ManageChatsUseCase;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import com.autoapplicant.port.out.chat.ChatPlanRepositoryPort;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Desktop only: the chat runs the server owner's own Claude Code login. */
@Service
@Profile("desktop")
public class ChatService implements ManageChatsUseCase {

    private static final int MAX_TITLE_LENGTH = 120;

    private final ChatRepositoryPort chats;
    private final ChatAgentPort agent;
    private final Clock clock;
    private final ChatTurns running;
    private final ChatActions actions;
    private final ChatPlanRepositoryPort plans;

    public ChatService(ChatRepositoryPort chats, ChatAgentPort agent, Clock clock,
                       ChatTurns running, JobRepositoryPort jobs, ChatPlanRepositoryPort plans) {
        this.chats = chats;
        this.agent = agent;
        this.clock = clock;
        this.running = running;
        this.actions = new ChatActions(jobs);
        this.plans = plans;
    }

    @Override
    public List<Chat> list(UUID userId) {
        return chats.findByUserId(userId);
    }

    @Override
    public Chat create(UUID userId) {
        Instant now = clock.instant();
        return chats.save(new Chat(null, userId, UUID.randomUUID(), null, now, now));
    }

    @Override
    public Chat rename(UUID userId, UUID chatId, String title) {
        Chat chat = OwnedChats.require(chats, userId, chatId);
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "A chat title needs 1 to " + MAX_TITLE_LENGTH + " characters");
        }
        return chats.save(chat.withTitle(trimmed));
    }

    @Override
    public void delete(UUID userId, UUID chatId) {
        Chat chat = OwnedChats.require(chats, userId, chatId);
        // Stopped first: a running claude would go on writing the transcript forget deletes.
        running.stop(chat.id());
        chats.deleteById(chat.id());
        agent.forget(chat.sessionId());
    }

    @Override
    public ChatTranscript messages(UUID userId, UUID chatId) {
        Chat chat = OwnedChats.require(chats, userId, chatId);
        List<ChatMessage> messages = chats.findMessages(chat.id()).stream()
                .map(this::withCheckedActions)
                .toList();
        return new ChatTranscript(messages, !messages.isEmpty() && !agent.hasSession(chat.sessionId()),
                plansOf(userId, messages));
    }

    /** Read as they stand now, so a card shows what she decided and how it went. */
    private List<ChatPlan> plansOf(UUID userId, List<ChatMessage> messages) {
        Set<UUID> ids = messages.stream()
                .flatMap(m -> m.planIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return plans.findByIds(ids).stream()
                .filter(plan -> plan.userId().equals(userId))
                .toList();
    }

    /** Buttons are checked again on every read: a posting may have gone since it was offered. */
    private ChatMessage withCheckedActions(ChatMessage message) {
        if (message.actions().isEmpty()) {
            return message;
        }
        return message.withActions(message.actions().stream()
                .flatMap(a -> actions.resolve(a.kind(), a.postingIds()).stream())
                .toList());
    }
}
