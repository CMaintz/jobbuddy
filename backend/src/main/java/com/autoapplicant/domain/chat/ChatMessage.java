package com.autoapplicant.domain.chat;

import java.time.Instant;

/** One message of a transcript as the chat shows it. */
public record ChatMessage(Role role, String text, Instant at) {

    public enum Role { USER, ASSISTANT }
}
