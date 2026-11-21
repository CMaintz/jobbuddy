package com.autoapplicant.domain.chat;

import java.time.Instant;
import java.util.List;

/**
 * One message of a transcript as the chat shows it.
 *
 * @param actions the buttons offered with an answer; empty on her own messages
 */
public record ChatMessage(Role role, String text, Instant at, List<ChatAction> actions) {

    public ChatMessage {
        actions = List.copyOf(actions);
    }

    public ChatMessage(Role role, String text, Instant at) {
        this(role, text, at, List.of());
    }

    public ChatMessage withActions(List<ChatAction> newActions) {
        return new ChatMessage(role, text, at, newActions);
    }

    public enum Role { USER, ASSISTANT }
}
