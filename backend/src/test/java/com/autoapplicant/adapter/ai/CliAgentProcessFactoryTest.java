package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void createDropsBackendSecretsFromTheChildEnvironment() {
        ProcessBuilder pb = CliAgentProcessFactory.create("claude -p", tempDir);

        assertThat(pb.environment().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("SPRING_APPLICATION_JSON"))
                .noneMatch(name -> name.equalsIgnoreCase("JAVA_HOME"));
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
