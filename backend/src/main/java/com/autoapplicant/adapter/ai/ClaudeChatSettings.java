package com.autoapplicant.adapter.ai;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * How the chat runs Claude Code.
 *
 * @param program the executable and any leading arguments, before the chat's own flags
 * @param workDir the chat's working directory. Claude Code files a session under its cwd and
 *     {@code --resume} looks it up there, so this must stay the same across app starts
 * @param claudeConfigDir where Claude Code keeps its sessions ({@code CLAUDE_CONFIG_DIR}, else
 *     {@code ~/.claude})
 * @param model the model the chat is pinned to
 * @param timeout the longest one turn may run
 */
record ClaudeChatSettings(List<String> program, Path workDir, Path claudeConfigDir, String model,
                          Duration timeout) {

    private static final String CONFIG_DIR_VARIABLE = "CLAUDE_CONFIG_DIR";

    /** Claude Code's own default when {@code CLAUDE_CONFIG_DIR} is unset. */
    static Path defaultClaudeConfigDir() {
        String configured = System.getenv(CONFIG_DIR_VARIABLE);
        return configured != null && !configured.isBlank()
                ? Path.of(configured)
                : Path.of(System.getProperty("user.home"), ".claude");
    }
}
