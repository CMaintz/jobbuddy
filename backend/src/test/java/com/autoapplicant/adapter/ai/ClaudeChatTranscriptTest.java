package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.domain.chat.ChatMessage.Role;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Session files shaped like the ones Claude Code 2.1 keeps under its projects directory. */
class ClaudeChatTranscriptTest {

    @TempDir Path appData;
    @TempDir Path claudeConfig;

    private final UUID session = UUID.randomUUID();

    @Test
    void shows_her_messages_and_the_answers_text_only() throws Exception {
        writeSession(
                "{\"type\":\"queue-operation\",\"operation\":\"enqueue\",\"content\":\"Hej\"}",
                user("2026-11-21T14:00:37.835Z", "\"Hvad er 2+2? Husk tallet 7.\\n\""),
                "{\"type\":\"attachment\",\"isSidechain\":false,\"attachment\":{}}",
                assistant("2026-11-21T14:00:42.040Z",
                        "{\"type\":\"thinking\",\"thinking\":\"the user asks\"}"),
                assistant("2026-11-21T14:00:42.169Z",
                        "{\"type\":\"text\",\"text\":\"2+2 er 4. Jeg husker 7.\"}"),
                "{\"type\":\"last-prompt\",\"lastPrompt\":\"Hvad er 2+2?\"}",
                user("2026-11-21T14:01:26.475Z",
                        "[{\"type\":\"text\",\"text\":\"Hvilket tal?\"}]"),
                assistant("2026-11-21T14:01:27.554Z",
                        "{\"type\":\"text\",\"text\":\"Tallet 7.\"}"));

        assertThat(agent().transcript(session)).containsExactly(
                new ChatMessage(Role.USER, "Hvad er 2+2? Husk tallet 7.",
                        Instant.parse("2026-11-21T14:00:37.835Z")),
                new ChatMessage(Role.ASSISTANT, "2+2 er 4. Jeg husker 7.",
                        Instant.parse("2026-11-21T14:00:42.169Z")),
                new ChatMessage(Role.USER, "Hvilket tal?", Instant.parse("2026-11-21T14:01:26.475Z")),
                new ChatMessage(Role.ASSISTANT, "Tallet 7.",
                        Instant.parse("2026-11-21T14:01:27.554Z")));
    }

    @Test
    void tool_results_and_side_conversations_are_not_messages() throws Exception {
        writeSession(
                user("2026-11-21T14:00:00Z", "\"Find opslag\""),
                assistant("2026-11-21T14:00:01Z", "{\"type\":\"text\",\"text\":\"Jeg søger.\"}"),
                user("2026-11-21T14:00:02Z", "[{\"type\":\"tool_result\",\"tool_use_id\":\"t1\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"posting data\"}]}]"),
                user("2026-11-21T14:00:03Z", "\"subagent prompt\"").replace(
                        "\"isSidechain\":false", "\"isSidechain\":true"),
                assistant("2026-11-21T14:00:04Z", "{\"type\":\"text\",\"text\":\"Her er tre.\"}"));

        List<ChatMessage> messages = agent().transcript(session);

        assertThat(messages).extracting(ChatMessage::role, ChatMessage::text).containsExactly(
                org.assertj.core.groups.Tuple.tuple(Role.USER, "Find opslag"),
                org.assertj.core.groups.Tuple.tuple(Role.ASSISTANT, "Jeg søger.\n\nHer er tre."));
    }

    @Test
    void a_session_that_never_ran_has_no_messages() {
        assertThat(agent().transcript(session)).isEmpty();
    }

    @Test
    void forget_deletes_the_session_file() throws Exception {
        Path file = writeSession(user("2026-11-21T14:00:00Z", "\"privat\""));

        agent().forget(session);

        assertThat(file).doesNotExist();
        assertThat(agent().transcript(session)).isEmpty();
    }

    private ClaudeChatAgent agent() {
        return new ClaudeChatAgent(new ClaudeChatSettings(List.of("claude"),
                appData.resolve("chat"), claudeConfig, "sonnet", Duration.ofSeconds(1)));
    }

    private Path writeSession(String... lines) throws Exception {
        Path dir = claudeConfig.resolve("projects").resolve("C--Users-her-AppData-Local-JobBuddy-chat");
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(session + ".jsonl"), String.join("\n", lines) + "\n");
    }

    private static String user(String at, String contentJson) {
        return "{\"type\":\"user\",\"isSidechain\":false,\"timestamp\":\"" + at + "\","
                + "\"message\":{\"role\":\"user\",\"content\":" + contentJson + "}}";
    }

    private static String assistant(String at, String blockJson) {
        return "{\"type\":\"assistant\",\"isSidechain\":false,\"timestamp\":\"" + at + "\","
                + "\"message\":{\"role\":\"assistant\",\"content\":[" + blockJson + "]}}";
    }
}
