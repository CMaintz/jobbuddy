package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The session files Claude Code keeps under its projects directory, found by session id. */
class ClaudeChatSessionsTest {

    @TempDir Path appData;
    @TempDir Path claudeConfig;

    private final UUID session = UUID.randomUUID();

    @Test
    void a_session_exists_once_claude_code_has_written_its_file() throws Exception {
        assertThat(agent().hasSession(session)).isFalse();

        writeSession();

        assertThat(agent().hasSession(session)).isTrue();
    }

    @Test
    void forget_deletes_the_session_file_and_the_folder_beside_it() throws Exception {
        Path file = writeSession();
        Path toolOutput = Files.createDirectories(file.resolveSibling(session.toString()));
        Files.writeString(toolOutput.resolve("result.txt"), "privat");

        agent().forget(session);

        assertThat(file).doesNotExist();
        assertThat(toolOutput).doesNotExist();
        assertThat(agent().hasSession(session)).isFalse();
    }

    private ClaudeChatAgent agent() {
        return new ClaudeChatAgent(new ClaudeChatSettings(List.of("claude"),
                appData.resolve("chat"), claudeConfig, "sonnet", Duration.ofSeconds(1)));
    }

    private Path writeSession() throws Exception {
        Path dir = claudeConfig.resolve("projects").resolve("C--Users-her-AppData-Local-JobBuddy-chat");
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(session + ".jsonl"), "{}\n");
    }
}
