package com.autoapplicant.domain.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * One of the user's saved conversations. The transcript itself is the Claude Code session
 * {@code sessionId}; only this metadata is kept in the database.
 *
 * @param title null until the first message names it, so the UI can show its own default
 */
public record Chat(UUID id, UUID userId, UUID sessionId, String title, Instant createdAt,
                   Instant lastActivityAt) {

    public Chat withTitle(String newTitle) {
        return new Chat(id, userId, sessionId, newTitle, createdAt, lastActivityAt);
    }

    public Chat touchedAt(Instant at) {
        return new Chat(id, userId, sessionId, title, createdAt, at);
    }
}
