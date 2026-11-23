package com.autoapplicant.adapter.web.dto.chat;

import com.autoapplicant.domain.chat.Chat;
import java.time.Instant;
import java.util.UUID;

/** A chat as the list shows it. The session id and owner stay on the server. */
public record ChatView(UUID id, String title, Instant createdAt, Instant lastActivityAt) {

    public static ChatView of(Chat chat) {
        return new ChatView(chat.id(), chat.title(), chat.createdAt(), chat.lastActivityAt());
    }
}
