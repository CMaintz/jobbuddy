package com.autoapplicant.port.in.chat;

import com.autoapplicant.domain.chat.ChatEvent;
import java.util.UUID;
import java.util.function.Consumer;

/** Sends her messages to the local agent, one turn at a time per chat. */
public interface ChatTurnUseCase {

    /**
     * Starts a turn and returns at once; {@code events} gets the answer and then one terminal
     * event (see {@link ChatEvent}).
     *
     * @throws com.autoapplicant.domain.common.NotFoundException the chat is not hers
     * @throws com.autoapplicant.domain.chat.ChatRejectedException the chat is already answering,
     *     or too many chats are
     */
    void send(UUID userId, UUID chatId, String message, Consumer<ChatEvent> events);

    /** Stops the chat's running turn, if any. */
    void stop(UUID userId, UUID chatId);
}
