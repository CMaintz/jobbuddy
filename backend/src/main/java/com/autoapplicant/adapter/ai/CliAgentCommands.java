package com.autoapplicant.adapter.ai;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * {@code --setting-sources=} stops the user's own settings (global CLAUDE.md, hooks, plugins)
     * from steering the generated text while keeping their login; {@code --bare} would also do
     * that but refuses OAuth, so it cannot run on a subscription.
     */
    static final String CLAUDE_DEFAULT = "claude -p --tools= --strict-mcp-config"
            + " --no-session-persistence --setting-sources=";

    /**
     * Without {@code --model} the child runs the user's own default, often the model that uses up
     * a subscription's quota fastest. An alias rather than a dated id, so a CLI update cannot
     * leave it pointing at a retired model.
     */
    static final String CLAUDE_DEFAULT_MODEL = "sonnet";

    /**
     * Codex has no switch that removes its tools, so its shell runs under a read-only sandbox.
     * {@code --skip-git-repo-check} is required because the work dir is not a git repo,
     * {@code --ignore-user-config} drops MCP servers from config.toml (auth still loads),
     * {@code --ephemeral} skips the rollout file, and {@code -} reads the prompt from stdin.
     */
    static final String CODEX_DEFAULT = "codex exec --sandbox read-only --skip-git-repo-check"
            + " --ignore-user-config --ephemeral -";

    private static final Logger log = LoggerFactory.getLogger(CliAgentCommands.class);

    /** Up to two adapters resolve at startup (generation, enrichment chat); one warning is enough. */
    private static final AtomicBoolean OVERRIDE_WARNED = new AtomicBoolean();

    private CliAgentCommands() {}

    /**
     * The operator's {@code override} when set, else the sandboxed default for {@code provider}.
     * The Claude default also gets {@code --model}; an override and Codex are left as they are.
     */
    static String resolve(String provider, String override, String model) {
        if (isSet(override)) {
            warnOnceAboutOverride(override);
            return override;
        }
        String pinned = pinnedModel(provider, override, model);
        return pinned == null ? CODEX_DEFAULT : CLAUDE_DEFAULT + " --model " + pinned;
    }

    /**
     * The model {@link #resolve} passes with {@code --model}, or {@code null} when it passes none.
     * {@code model} names a Claude model, so Codex never gets it, and an override is taken as is.
     */
    static String pinnedModel(String provider, String override, String model) {
        if (isSet(override) || CODEX_PROVIDER.equalsIgnoreCase(provider)) {
            return null;
        }
        return isSet(model) ? model.strip() : CLAUDE_DEFAULT_MODEL;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static void warnOnceAboutOverride(String override) {
        if (OVERRIDE_WARNED.compareAndSet(false, true)) {
            log.warn("AI_CLI_COMMAND '{}' replaces the sandboxed default command; prompts carry "
                    + "untrusted posting text, so it must supply its own no-tools/sandbox flags",
                    override);
        }
    }
}
