package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.port.in.chat.ChatTurnUseCase;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Runs chat turns, one at a time per chat and a few at once ({@link ChatTurns}).
 */
@Service
@Profile("desktop")
public class ChatTurnService implements ChatTurnUseCase {

    /** Long enough to recognise the chat in the list; the rest is in the transcript. */
    private static final int TITLE_LENGTH = 60;

    private static final String ELLIPSIS = "…";

    /** Room for a pasted posting; far more than a chat message needs otherwise. */
    static final int MAX_MESSAGE_CHARS = 20_000;

    private final ChatRepositoryPort chats;
    private final ChatAgentPort agent;
    private final Clock clock;
    private final ChatTurns running;

    public ChatTurnService(ChatRepositoryPort chats, ChatAgentPort agent, Clock clock,
                           ChatTurns running) {
        this.chats = chats;
        this.agent = agent;
        this.clock = clock;
        this.running = running;
    }

    /**
     * The chat and the slot are freed by the turn's terminal event, or here when the turn never
     * got going (the database or the agent failed first), so neither can leak.
     */
    @Override
    public void send(UUID userId, UUID chatId, String message, Consumer<ChatEvent> events) {
        String text = requireMessage(message);
        Chat chat = OwnedChats.require(chats, userId, chatId);
        ChatTurns.ActiveTurn turn = running.claim(chatId);
        try {
            chats.save(named(chat, text).touchedAt(clock.instant()));
            turn.attach(agent.start(chat.sessionId(), text, turn.releasingOnEnd(events)));
        } catch (RuntimeException e) {
            turn.release();
            throw e;
        }
    }

    /** Idempotent: a chat that is idle, gone or someone else's has nothing of hers to stop. */
    @Override
    public void stop(UUID userId, UUID chatId) {
        chats.findById(chatId)
                .filter(chat -> chat.userId().equals(userId))
                .ifPresent(chat -> running.stop(chatId));
    }

    private static String requireMessage(String message) {
        String text = message == null ? "" : message.strip();
        if (text.isEmpty() || text.length() > MAX_MESSAGE_CHARS) {
            throw new IllegalArgumentException(
                    "A chat message needs 1 to " + MAX_MESSAGE_CHARS + " characters");
        }
        return text;
    }

    private static Chat named(Chat chat, String firstMessage) {
        if (chat.title() != null) {
            return chat;
        }
        String oneLine = firstMessage.strip().replaceAll("\\s+", " ");
        if (oneLine.length() <= TITLE_LENGTH) {
            return chat.withTitle(oneLine);
        }
        String cut = oneLine.substring(0, TITLE_LENGTH);
        int lastSpace = cut.lastIndexOf(' ');
        return chat.withTitle((lastSpace > 0 ? cut.substring(0, lastSpace) : cut) + ELLIPSIS);
    }
}
