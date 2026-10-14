package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.config.AppProperties;
import org.junit.jupiter.api.Test;

class CliAgentCommandsTest {

    @Test
    void configuredCommandIsBlankSoTheProviderDefaultApplies() {
        assertThat(new AppProperties.Cli().getCommand()).isBlank();
    }

    @Test
    void claudeDefaultDisablesToolsAndMcpAndSessionTranscripts() {
        assertThat(CliAgentProcessFactory.parseCommand(CliAgentCommands.resolve("claude-cli", "")))
                .containsExactly(
                        "claude", "-p", "--tools=", "--strict-mcp-config", "--no-session-persistence");
    }

    @Test
    void genericCliProviderDefaultsToSandboxedClaude() {
        assertThat(CliAgentCommands.resolve("cli", null)).isEqualTo(CliAgentCommands.CLAUDE_DEFAULT);
    }

    @Test
    void codexDefaultRunsOutsideGitUnderReadOnlySandboxWithPromptOnStdin() {
        assertThat(CliAgentProcessFactory.parseCommand(CliAgentCommands.resolve("codex", " ")))
                .containsExactly(
                        "codex",
                        "exec",
                        "--sandbox",
                        "read-only",
                        "--skip-git-repo-check",
                        "--ignore-user-config",
                        "--ephemeral",
                        "-");
    }

    @Test
    void operatorOverrideReplacesTheDefault() {
        assertThat(CliAgentCommands.resolve("codex", "my-agent --flag")).isEqualTo("my-agent --flag");
    }
}
