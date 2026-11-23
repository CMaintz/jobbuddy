package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.config.AppProperties;
import org.junit.jupiter.api.Test;

class CliAgentCommandsTest {

    @Test
    void configuredCommandIsBlankSoTheProviderDefaultApplies() {
        assertThat(new AppProperties.Cli().getCommand()).isBlank();
    }

    // --setting-sources= keeps the user's own ~/.claude (CLAUDE.md, hooks, plugins) out of the
    // generated text; --model keeps the run off whatever default model burns their quota fastest.
    @Test
    void claudeDefaultIsSandboxedIsolatedFromUserSettingsAndPinnedToAModel() {
        assertThat(CliAgentProcessFactory.parseCommand(CliAgentCommands.resolve("claude-cli", "", "")))
                .containsExactly(
                        "claude", "-p", "--tools=", "--strict-mcp-config", "--no-session-persistence",
                        "--setting-sources=", "--model", "sonnet");
    }

    @Test
    void configuredModelReplacesThePinnedDefault() {
        assertThat(CliAgentProcessFactory.parseCommand(CliAgentCommands.resolve("claude-cli", "", "opus")))
                .endsWith("--setting-sources=", "--model", "opus");
    }

    @Test
    void genericCliProviderDefaultsToSandboxedClaude() {
        assertThat(CliAgentCommands.resolve("cli", null, null))
                .isEqualTo(CliAgentCommands.resolve("claude-cli", null, null));
    }

    @Test
    void codexDefaultRunsOutsideGitUnderReadOnlySandboxWithPromptOnStdin() {
        // The model setting names a Claude model; passing it to Codex would fail every call.
        assertThat(CliAgentProcessFactory.parseCommand(CliAgentCommands.resolve("codex", " ", "sonnet")))
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
        assertThat(CliAgentCommands.resolve("claude-cli", "my-agent --flag", "opus"))
                .isEqualTo("my-agent --flag");
    }
}
