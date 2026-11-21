package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Holds every turn open until the test finishes it. */
final class FakeChatAgent implements ChatAgentPort {

    record Turn(UUID userId, UUID sessionId, String message, Consumer<ChatEvent> events) {}

    final List<Turn> turns = new ArrayList<>();
    final Set<UUID> forgotten = new HashSet<>();
    /** What happened to sessions, in order: "stop:<id>" and "forget:<id>". */
    final List<String> operations = new ArrayList<>();
    /** The sessions the agent still has; a turn creates its session, as Claude Code does. */
    final Set<UUID> sessions = new HashSet<>();
    ChatEvent failImmediately;

    @Override
    public RunningTurn start(UUID userId, UUID sessionId, String message,
                             Consumer<ChatEvent> events) {
        turns.add(new Turn(userId, sessionId, message, events));
        sessions.add(sessionId);
        if (failImmediately != null) {
            events.accept(failImmediately);
        }
        return () -> {
            operations.add("stop:" + sessionId);
            events.accept(new ChatEvent.Stopped());
        };
    }

    @Override
    public boolean hasSession(UUID sessionId) {
        return sessions.contains(sessionId);
    }

    @Override
    public void forget(UUID sessionId) {
        forgotten.add(sessionId);
        operations.add("forget:" + sessionId);
    }

    Turn last() {
        return turns.get(turns.size() - 1);
    }
}
