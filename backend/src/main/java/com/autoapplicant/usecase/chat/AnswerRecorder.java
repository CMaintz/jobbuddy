package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the answer she saw, however the turn ended: a stopped or failed answer is kept as far as
 * it got. It is stored before the terminal event is passed on, because that event frees the chat
 * for her next message, which must come after this answer.
 */
final class AnswerRecorder implements Consumer<ChatEvent> {

    private static final Logger log = LoggerFactory.getLogger(AnswerRecorder.class);

    private final ChatRepositoryPort chats;
    private final UUID chatId;
    private final Clock clock;
    private final Consumer<ChatEvent> next;
    private final StringBuilder answer = new StringBuilder();

    AnswerRecorder(ChatRepositoryPort chats, UUID chatId, Clock clock, Consumer<ChatEvent> next) {
        this.chats = chats;
        this.chatId = chatId;
        this.clock = clock;
        this.next = next;
    }

    @Override
    public void accept(ChatEvent event) {
        if (event instanceof ChatEvent.Delta delta) {
            answer.append(delta.text());
        }
        try {
            if (event.isTerminal() && !answer.isEmpty()) {
                chats.appendMessage(chatId,
                        new ChatMessage(ChatMessage.Role.ASSISTANT, answer.toString(), clock.instant()));
            }
        } catch (RuntimeException e) {
            log.warn("Could not keep the answer of chat {}", chatId, e);
        } finally {
            next.accept(event);
        }
    }
}
