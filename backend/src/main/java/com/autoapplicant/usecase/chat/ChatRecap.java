package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatMessage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The start of a turn whose session Claude Code has deleted: the latest messages she can still
 * see, so the answer does not start from nothing. Short on purpose; it is context, not a replay.
 */
final class ChatRecap {

    static final int MESSAGES = 10;
    static final int CHARS_PER_MESSAGE = 400;

    private static final String HEADER = "[JobBuddy: Den tidligere samtale er ikke længere"
            + " tilgængelig for dig. Her er de seneste beskeder, som brugeren stadig kan se, som"
            + " baggrund.]";
    private static final String FOOTER = "[Slut på resumé. Brugerens nye besked følger.]";

    private ChatRecap() {}

    static String before(List<ChatMessage> earlier, String message) {
        String lines = earlier.stream()
                .skip(Math.max(0, earlier.size() - MESSAGES))
                .map(ChatRecap::line)
                .collect(Collectors.joining("\n"));
        return HEADER + "\n" + lines + "\n" + FOOTER + "\n\n" + message;
    }

    private static String line(ChatMessage m) {
        String who = m.role() == ChatMessage.Role.USER ? "Brugeren" : "Assistenten";
        String text = m.text().length() > CHARS_PER_MESSAGE
                ? m.text().substring(0, CHARS_PER_MESSAGE) + " [...]"
                : m.text();
        return who + ": " + text;
    }
}
