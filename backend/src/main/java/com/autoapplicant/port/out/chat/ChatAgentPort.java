package com.autoapplicant.port.out.chat;

import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatMessage;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** The local agent that answers chat turns and keeps each conversation's transcript. */
public interface ChatAgentPort {

    /**
     * Starts one turn of session {@code sessionId} in the background and returns at once.
     * {@code events} gets the answer as it is written and then exactly one terminal event, on a
     * thread of the agent's own; the session is created on its first turn and resumed after.
     */
    RunningTurn start(UUID sessionId, String message, Consumer<ChatEvent> events);

    /** The session's messages so far, oldest first; empty for a session that never ran. */
    List<ChatMessage> transcript(UUID sessionId);

    /** Deletes the session's transcript, which holds her own words. */
    void forget(UUID sessionId);

    /** A turn in progress. */
    interface RunningTurn {

        /** Ends the turn now; its terminal event is {@link ChatEvent.Stopped}. Idempotent. */
        void stop();
    }
}
