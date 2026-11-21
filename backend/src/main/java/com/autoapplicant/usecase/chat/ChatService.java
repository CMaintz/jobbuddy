package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.port.in.chat.ManageChatsUseCase;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    public ChatService(ChatRepositoryPort chats, ChatAgentPort agent, Clock clock,
                       ChatTurns running) {
        this.chats = chats;
        this.agent = agent;
        this.clock = clock;
        this.running = running;
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
    public List<ChatMessage> messages(UUID userId, UUID chatId) {
        return agent.transcript(OwnedChats.require(chats, userId, chatId).sessionId());
    }
}
