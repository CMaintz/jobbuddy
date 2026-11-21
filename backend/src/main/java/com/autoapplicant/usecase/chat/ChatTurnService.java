package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.autoapplicant.domain.chat.ChatRejectedException;
import com.autoapplicant.port.in.chat.ChatTurnUseCase;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Runs chat turns: one at a time per chat, since two processes resuming the same Claude Code
 * session would interleave its transcript, and only a few chats at once, since each turn is a
 * {@code claude} process on the same subscription that generation and enrichment use.
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
    private final Semaphore slots;
    private final Map<UUID, ActiveTurn> active = new ConcurrentHashMap<>();

    public ChatTurnService(ChatRepositoryPort chats,
                           ChatAgentPort agent,
                           Clock clock,
                           @Value("${app.chat.max-concurrent-turns:2}") int maxConcurrentTurns) {
        this.chats = chats;
        this.agent = agent;
        this.clock = clock;
        this.slots = new Semaphore(maxConcurrentTurns);
    }

    @Override
    public void send(UUID userId, UUID chatId, String message, Consumer<ChatEvent> events) {
        String text = requireMessage(message);
        Chat chat = OwnedChats.require(chats, userId, chatId);
        ActiveTurn turn = new ActiveTurn();
        if (active.putIfAbsent(chatId, turn) != null) {
            throw new ChatRejectedException(ChatFailure.CHAT_BUSY);
        }
        if (!slots.tryAcquire()) {
            active.remove(chatId, turn);
            throw new ChatRejectedException(ChatFailure.TOO_MANY_CHATS);
        }
        chats.save(named(chat, text).touchedAt(clock.instant()));
        turn.attach(agent.start(chat.sessionId(), text, event -> {
            if (event.isTerminal() && active.remove(chatId, turn)) {
                slots.release();
            }
            events.accept(event);
        }));
    }

    @Override
    public void stop(UUID userId, UUID chatId) {
        OwnedChats.require(chats, userId, chatId);
        ActiveTurn turn = active.get(chatId);
        if (turn != null) {
            turn.stop();
        }
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

    /**
     * A turn from the moment it claims its chat. A stop that arrives before the agent has handed
     * back its handle is remembered and applied once it does.
     */
    private static final class ActiveTurn {

        private ChatAgentPort.RunningTurn running;
        private boolean stopRequested;

        synchronized void attach(ChatAgentPort.RunningTurn handle) {
            running = handle;
            if (stopRequested) {
                handle.stop();
            }
        }

        synchronized void stop() {
            stopRequested = true;
            if (running != null) {
                running.stop();
            }
        }
    }
}
