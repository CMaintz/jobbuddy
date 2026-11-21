package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Holds every turn open until the test finishes it. */
final class FakeChatAgent implements ChatAgentPort {

    record Turn(UUID sessionId, String message, Consumer<ChatEvent> events) {}

    final List<Turn> turns = new ArrayList<>();
    final Set<UUID> forgotten = new HashSet<>();
    final Map<UUID, List<ChatMessage>> transcripts = new HashMap<>();
    ChatEvent failImmediately;

    @Override
    public RunningTurn start(UUID sessionId, String message, Consumer<ChatEvent> events) {
        turns.add(new Turn(sessionId, message, events));
        if (failImmediately != null) {
            events.accept(failImmediately);
        }
        return () -> events.accept(new ChatEvent.Stopped());
    }

    @Override
    public List<ChatMessage> transcript(UUID sessionId) {
        return transcripts.getOrDefault(sessionId, List.of());
    }

    @Override
    public void forget(UUID sessionId) {
        forgotten.add(sessionId);
    }

    Turn last() {
        return turns.get(turns.size() - 1);
    }
}
