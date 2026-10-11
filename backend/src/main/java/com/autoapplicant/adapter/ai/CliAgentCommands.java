package com.autoapplicant.adapter.ai;

/**
 * The command line run for each CLI-agent provider. Prompts carry crawled posting text, so each
 * default turns the agent's tools off (or confines them) and keeps it from writing a session
 * transcript of the CV to disk. An {@code app.ai.cli.command} override replaces the whole line,
 * sandbox flags included.
 */
final class CliAgentCommands {

    static final String CODEX_PROVIDER = "codex";

    /**
     * Claude Code: {@code --tools=} removes every built-in tool, {@code --strict-mcp-config} drops
     * configured MCP servers, {@code --no-session-persistence} skips the per-cwd transcript.
     */
    static final String CLAUDE_DEFAULT =
            "claude -p --tools= --strict-mcp-config --no-session-persistence";

    /**
     * Codex has no switch that removes its tools, so its shell runs under a read-only sandbox.
     * {@code --skip-git-repo-check} is required because the work dir is not a git repo,
     * {@code --ignore-user-config} drops MCP servers from config.toml (auth still loads),
     * {@code --ephemeral} skips the rollout file, and {@code -} reads the prompt from stdin.
     */
    static final String CODEX_DEFAULT = "codex exec --sandbox read-only --skip-git-repo-check"
            + " --ignore-user-config --ephemeral -";

    private CliAgentCommands() {}

    /** The operator's {@code override} when set, else the sandboxed default for {@code provider}. */
    static String resolve(String provider, String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        return CODEX_PROVIDER.equalsIgnoreCase(provider) ? CODEX_DEFAULT : CLAUDE_DEFAULT;
    }
}
