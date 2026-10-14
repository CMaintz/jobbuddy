package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class CliAgentExecutableTest {

    @TempDir
    Path binDir;

    @Test
    void windowsFindsAnNpmCmdShimOnPath() throws IOException {
        Path shim = Files.writeString(binDir.resolve("codex.cmd"), "@echo off");
        Map<String, String> env = Map.of("Path", pathWith(binDir), "PATHEXT", ".exe;.cmd");

        List<String> argv = CliAgentExecutable.resolve(List.of("codex", "exec", "-"), env, true);

        assertThat(argv).containsExactly(shim.toString(), "exec", "-");
    }

    @Test
    void anExeEarlierInPathextWinsOverACmd() throws IOException {
        Files.writeString(binDir.resolve("claude.cmd"), "@echo off");
        Path exe = Files.writeString(binDir.resolve("claude.exe"), "");
        Map<String, String> env = Map.of("PATH", pathWith(binDir), "PATHEXT", ".exe;.cmd");

        assertThat(CliAgentExecutable.resolve(List.of("claude"), env, true).get(0))
                .isEqualTo(exe.toString());
    }

    @Test
    void aBatchFileArgumentCmdWouldInterpretIsRefused() throws IOException {
        Files.writeString(binDir.resolve("codex.cmd"), "@echo off");
        Map<String, String> env = Map.of("PATH", pathWith(binDir), "PATHEXT", ".cmd");

        assertThatThrownBy(() -> CliAgentExecutable.resolve(
                        List.of("codex", "exec&calc"), env, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cmd.exe");
    }

    @Test
    void aBatchFileNamedWithATrailingDotIsStillGuarded() {
        Map<String, String> env = Map.of("PATH", pathWith(binDir), "PATHEXT", ".cmd");

        assertThatThrownBy(() -> CliAgentExecutable.resolve(
                        List.of("claude.cmd.", "-p&calc"), env, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cmd.exe");
    }

    @Test
    void outsideWindowsArgvIsLeftToTheOs() throws IOException {
        Files.writeString(binDir.resolve("codex.cmd"), "@echo off");
        Map<String, String> env = Map.of("PATH", pathWith(binDir));
        List<String> argv = List.of("codex", "exec&x");

        assertThat(CliAgentExecutable.resolve(argv, env, false)).isSameAs(argv);
    }

    @Test
    void anUnknownProgramIsKeptSoTheLaunchFailsWithItsName() {
        Map<String, String> env = Map.of("PATH", pathWith(binDir));

        assertThat(CliAgentExecutable.resolve(List.of("no-such-agent", "-p"), env, true))
                .containsExactly("no-such-agent", "-p");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aResolvedCmdShimActuallyRunsWithItsArguments() throws Exception {
        Path dir = Files.createDirectories(binDir.resolve("Program Files (x86)"));
        Files.writeString(dir.resolve("agent.cmd"), "@echo off\r\necho ARGS=%*\r\n");
        Map<String, String> env = Map.of("PATH", dir.toString(), "PATHEXT", ".exe;.cmd");
        List<String> argv = CliAgentExecutable.resolve(
                List.of("agent", "-p", "--tools=", "--sandbox", "read-only", "-"), env, true);

        CliAgentProcessRunner.Result result = CliAgentProcessRunner.run(
                new ProcessBuilder(argv), "", Duration.ofSeconds(30));

        assertThat(result.stdout().strip()).isEqualTo("ARGS=-p --tools= --sandbox read-only -");
    }

    private static String pathWith(Path dir) {
        return String.join(File.pathSeparator, "/no/such/dir", dir.toString());
    }
}
