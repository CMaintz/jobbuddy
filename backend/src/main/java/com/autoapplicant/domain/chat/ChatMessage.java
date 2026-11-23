package com.autoapplicant.domain.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One message of a transcript as the chat shows it.
 *
 * @param actions the buttons offered with an answer; empty on her own messages
 * @param planIds the plans an answer proposed, by id; their cards come from the plans themselves,
 *     as they stand now
 */
public record ChatMessage(Role role, String text, Instant at, List<ChatAction> actions,
                          List<UUID> planIds) {

    public ChatMessage {
        actions = List.copyOf(actions);
        planIds = List.copyOf(planIds);
    }

    public ChatMessage(Role role, String text, Instant at, List<ChatAction> actions) {
        this(role, text, at, actions, List.of());
    }

    public ChatMessage(Role role, String text, Instant at) {
        this(role, text, at, List.of(), List.of());
    }

    public ChatMessage withActions(List<ChatAction> newActions) {
        return new ChatMessage(role, text, at, newActions, planIds);
    }

    public enum Role { USER, ASSISTANT }
}
