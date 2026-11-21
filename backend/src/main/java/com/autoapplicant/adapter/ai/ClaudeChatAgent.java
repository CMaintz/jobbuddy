package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppDataDirectory;
import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The chat on the user's own Claude Code login. Each chat is one Claude Code session, started
 * with {@code --session-id} on its first turn and continued with {@code --resume} after. The
 * session is kept apart from her own Claude Code use: no user or project settings, no MCP
 * servers but the ones given here, no built-in tools and no slash commands.
 *
 * <p>Desktop only: on a hosted server this would run the server owner's login for every user.
 */
@Component
@Profile("desktop")
public class ClaudeChatAgent implements ChatAgentPort {

    private static final Logger log = LoggerFactory.getLogger(ClaudeChatAgent.class);

    private static final String CLAUDE_PROVIDER = "claude-cli";
    private static final String CHAT_DIR = "chat";
    private static final String SYSTEM_PROMPT_RESOURCE = "/chat/system-prompt.md";
    private static final String SYSTEM_PROMPT_FILE = "system-prompt.md";

    /**
     * {@code --verbose} is required with stream-json in print mode. {@code dontAsk} denies any
     * tool not allowed outright instead of waiting for an answer nobody can give.
     */
    private static final List<String> FLAGS = List.of(
            "-p",
            "--output-format", "stream-json",
            "--verbose",
            "--include-partial-messages",
            "--tools=",
            "--strict-mcp-config",
            "--setting-sources=",
            "--disable-slash-commands",
            "--permission-mode", "dontAsk");

    private final ClaudeChatSettings settings;
    private final ClaudeChatSessions sessions;

    @Autowired
    public ClaudeChatAgent(AppDataDirectory dataDirectory,
                           AppProperties props,
                           @Value("${app.chat.turn-timeout-seconds:300}") long timeoutSeconds) {
        this(new ClaudeChatSettings(
                List.of("claude"),
                dataDirectory.root().resolve(CHAT_DIR),
                ClaudeChatSettings.defaultClaudeConfigDir(),
                CliAgentCommands.pinnedModel(CLAUDE_PROVIDER, null, props.getAi().getCli().getModel()),
                Duration.ofSeconds(timeoutSeconds)));
    }

    ClaudeChatAgent(ClaudeChatSettings settings) {
        this.settings = settings;
        this.sessions = new ClaudeChatSessions(settings.claudeConfigDir());
    }

    @Override
    public RunningTurn start(UUID sessionId, String message, Consumer<ChatEvent> events) {
        ProcessBuilder pb;
        try {
            pb = CliAgentProcessFactory.create(
                    command(sessionId, systemPromptFile()), settings.workDir(), CLAUDE_PROVIDER);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            log.warn("Could not prepare the chat agent: {}", e.getMessage());
            events.accept(new ChatEvent.Failed(ChatFailure.FAILED));
            return () -> { };
        }
        return ClaudeChatTurn.start(pb, message, settings.timeout(), events);
    }

    @Override
    public boolean hasSession(UUID sessionId) {
        return sessions.exists(sessionId);
    }

    @Override
    public void forget(UUID sessionId) {
        sessions.delete(sessionId);
    }

    /**
     * A session id Claude Code already has is refused as new, and one it lacks cannot be
     * resumed. A turn killed early may or may not have written the file, so the file decides.
     */
    private List<String> command(UUID sessionId, Path systemPrompt) {
        List<String> argv = new ArrayList<>(settings.program());
        argv.addAll(FLAGS);
        argv.addAll(List.of("--model", settings.model(),
                "--system-prompt-file", systemPrompt.toString(),
                sessions.exists(sessionId) ? "--resume" : "--session-id", sessionId.toString()));
        return argv;
    }

    /**
     * Checked each turn so an app update's prompt reaches new chats, but only rewritten when it
     * changed: another chat's {@code claude} may be reading it at that moment.
     */
    private Path systemPromptFile() {
        Path file = settings.workDir().resolve(SYSTEM_PROMPT_FILE);
        try {
            byte[] wanted = systemPrompt();
            Files.createDirectories(settings.workDir());
            if (!Files.isRegularFile(file) || !Arrays.equals(Files.readAllBytes(file), wanted)) {
                Files.write(file, wanted);
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] systemPrompt() throws IOException {
        try (InputStream prompt = ClaudeChatAgent.class.getResourceAsStream(SYSTEM_PROMPT_RESOURCE)) {
            if (prompt == null) {
                throw new IllegalStateException("Missing resource " + SYSTEM_PROMPT_RESOURCE);
            }
            return prompt.readAllBytes();
        }
    }
}
