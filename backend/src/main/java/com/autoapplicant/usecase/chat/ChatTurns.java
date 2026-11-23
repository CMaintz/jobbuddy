package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.autoapplicant.domain.chat.ChatRejectedException;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The chat turns running now: one per chat, since two processes resuming one Claude Code session
 * would interleave its transcript, and only a few at once, since each is a {@code claude} process
 * on the subscription generation and enrichment also use. Shared by sending and deleting, so a
 * chat deleted mid-answer can have its turn stopped first.
 */
@Component
@Profile("desktop")
public class ChatTurns {

    private final Semaphore slots;
    private final Map<UUID, ActiveTurn> active = new ConcurrentHashMap<>();

    public ChatTurns(@Value("${app.chat.max-concurrent-turns:2}") int maxConcurrentTurns) {
        this.slots = new Semaphore(maxConcurrentTurns);
    }

    /**
     * Claims the chat and a slot for one turn.
     *
     * @throws ChatRejectedException the chat is answering already, or too many chats are
     */
    ActiveTurn claim(UUID chatId) {
        ActiveTurn turn = new ActiveTurn(chatId);
        if (active.putIfAbsent(chatId, turn) != null) {
            throw new ChatRejectedException(ChatFailure.CHAT_BUSY);
        }
        if (!slots.tryAcquire()) {
            active.remove(chatId, turn);
            throw new ChatRejectedException(ChatFailure.TOO_MANY_CHATS);
        }
        return turn;
    }

    /** Stops the chat's running turn, if it has one. */
    void stop(UUID chatId) {
        ActiveTurn turn = active.get(chatId);
        if (turn != null) {
            turn.stop();
        }
    }

    /** A turn from the moment it claims its chat until its terminal event frees it. */
    final class ActiveTurn {

        private final UUID chatId;
        private ChatAgentPort.RunningTurn running;
        private boolean stopRequested;

        private ActiveTurn(UUID chatId) {
            this.chatId = chatId;
        }

        /** {@code events}, freeing the chat and the slot on the terminal event. */
        Consumer<ChatEvent> releasingOnEnd(Consumer<ChatEvent> events) {
            return event -> {
                if (event.isTerminal()) {
                    release();
                }
                events.accept(event);
            };
        }

        /** Frees the chat and its slot; a second call does nothing. */
        void release() {
            if (active.remove(chatId, this)) {
                slots.release();
            }
        }

        /** A stop that came before the agent handed back its handle is applied now. */
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
