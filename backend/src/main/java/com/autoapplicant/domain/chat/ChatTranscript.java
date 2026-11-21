package com.autoapplicant.domain.chat;

import java.util.List;

/**
 * A chat as she sees it: her messages and the answers, as JobBuddy kept them.
 *
 * @param contextLost the agent no longer has the conversation itself (Claude Code clears old
 *     sessions), so it goes on from a short recap of these messages instead
 */
public record ChatTranscript(List<ChatMessage> messages, boolean contextLost) {

    public ChatTranscript {
        messages = List.copyOf(messages);
    }
}
