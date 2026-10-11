package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliAgentProcessFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createRunsTheCommandInTheGivenWorkDir() {
        ProcessBuilder pb = CliAgentProcessFactory.create("  codex   exec  ", tempDir);

        assertThat(pb.command()).isEqualTo(List.of("codex", "exec"));
        assertThat(pb.directory()).isEqualTo(tempDir.toFile());
    }

    @Test
    void createKeepsPathInTheChildEnvironment() {
        ProcessBuilder pb = CliAgentProcessFactory.create("claude -p", tempDir);

        assertThat(pb.environment().keySet())
                .anyMatch(name -> name.equalsIgnoreCase("PATH"));
    }

    @Test
    void scrubSecretsRemovesBackendSecretsButKeepsCliAuthAndBasics() {
        Map<String, String> env = new HashMap<>();
        env.put("OPENAI_API_KEY", "sk-x");
        env.put("GEMINI_API_KEY", "g");
        env.put("SECRET_ENCRYPTION_KEY", "k");
        env.put("DB_PASS", "p");
        env.put("POSTGRES_PASSWORD", "p");
        env.put("LINKEDIN_CLIENT_SECRET", "s");
        env.put("GOOGLE_APPLICATION_CREDENTIALS", "/secrets/sa.json");
        env.put("SPRING_DATASOURCE_PASSWORD", "p");
        env.put("openai_api_key", "lowercase");
        env.put("PATH", "/usr/bin");
        env.put("HOME", "/home/u");
        env.put("USERPROFILE", "C:\\Users\\u");
        env.put("ANTHROPIC_API_KEY", "cli-auth");
        env.put("CLAUDE_CONFIG_DIR", "/home/u/.claude");

        CliAgentProcessFactory.scrubSecrets(env);

        assertThat(env.keySet())
                .containsExactlyInAnyOrder(
                        "PATH", "HOME", "USERPROFILE", "ANTHROPIC_API_KEY", "CLAUDE_CONFIG_DIR");
    }

    @Test
    void workDirIsFreshEmptyAndDeletedWithItsContents() throws IOException {
        Path workDir = CliAgentProcessFactory.createWorkDir();
        try (Stream<Path> entries = Files.list(workDir)) {
            assertThat(entries).isEmpty();
        }
        Files.createDirectories(workDir.resolve("nested"));
        Files.writeString(workDir.resolve("nested/left-behind.txt"), "x");

        CliAgentProcessFactory.deleteWorkDir(workDir);

        assertThat(workDir).doesNotExist();
    }
}
