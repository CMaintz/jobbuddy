package com.autoapplicant.adapter.ai;

import com.autoapplicant.adapter.mcp.ChatToolAccess;
import com.autoapplicant.adapter.mcp.ChatToolAccess.ToolSession;
import com.autoapplicant.config.AppDataDirectory;
import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The chat on the user's own Claude Code login. Each chat is one Claude Code session, started
 * with {@code --session-id} on its first turn and continued with {@code --resume} after. The
 * session is kept apart from her own Claude Code use: no user or project settings, no built-in
 * tools and no slash commands. Its only MCP server is JobBuddy's own, opened for one turn at a
 * time, and only that server's tools are allowed.
 *
 * <p>Desktop only: on a hosted server this would run the server owner's login for every user.
 */
@Component
@Profile("desktop")
public class ClaudeChatAgent implements ChatAgentPort {

    private static final Logger log = LoggerFactory.getLogger(ClaudeChatAgent.class);

    private static final String CLAUDE_PROVIDER = "claude-cli";
    private static final String CHAT_DIR = "chat";

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
    private final ChatToolAccess tools;
    private final ClaudeChatSessions sessions;
    private final ClaudeChatFiles files;

    @Autowired
    public ClaudeChatAgent(AppDataDirectory dataDirectory,
                           AppProperties props,
                           ChatToolAccess tools,
                           @Value("${app.chat.turn-timeout-seconds:300}") long timeoutSeconds) {
        this(new ClaudeChatSettings(
                List.of("claude"),
                dataDirectory.root().resolve(CHAT_DIR),
                ClaudeChatSettings.defaultClaudeConfigDir(),
                CliAgentCommands.pinnedModel(CLAUDE_PROVIDER, null, props.getAi().getCli().getModel()),
                Duration.ofSeconds(timeoutSeconds)), tools);
    }

    /** {@code tools} may be null for a chat with no access to JobBuddy's data. */
    ClaudeChatAgent(ClaudeChatSettings settings, ChatToolAccess tools) {
        this.settings = settings;
        this.tools = tools;
        this.sessions = new ClaudeChatSessions(settings.claudeConfigDir());
        this.files = new ClaudeChatFiles(settings.workDir());
    }

    ClaudeChatAgent(ClaudeChatSettings settings) {
        this(settings, null);
    }

    /** The turn's tool access ends with the turn, so a leaked token is worth nothing after it. */
    @Override
    public RunningTurn start(UUID userId, UUID sessionId, String message,
                             Consumer<ChatEvent> events) {
        AtomicReference<Runnable> closeTools = new AtomicReference<>(() -> { });
        ClaudeChatTurn turn = new ClaudeChatTurn(event -> {
            if (event.isTerminal()) {
                closeTools.get().run();
            }
            events.accept(event);
        });
        Optional<ToolSession> session = Optional.ofNullable(tools)
                .map(access -> access.open(userId, turn::offer));
        session.ifPresent(s -> closeTools.set(s.close()));
        launch(turn, sessionId, message, session);
        return turn;
    }

    private void launch(ClaudeChatTurn turn, UUID sessionId, String message,
                        Optional<ToolSession> session) {
        try {
            ProcessBuilder pb = CliAgentProcessFactory.create(
                    command(sessionId, session), settings.workDir(), CLAUDE_PROVIDER);
            session.ifPresent(s -> pb.environment().put(ClaudeChatFiles.MCP_TOKEN_VARIABLE, s.token()));
            turn.launch(pb, message, settings.timeout());
        } catch (IllegalArgumentException | UncheckedIOException e) {
            log.warn("Could not prepare the chat agent: {}", e.getMessage());
            turn.fail(ChatFailure.FAILED);
        }
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
    private List<String> command(UUID sessionId, Optional<ToolSession> session) {
        List<String> argv = new ArrayList<>(settings.program());
        argv.addAll(FLAGS);
        session.ifPresent(s -> argv.addAll(toolFlags(s)));
        argv.addAll(List.of("--model", settings.model(),
                "--system-prompt-file", files.systemPrompt().toString(),
                sessions.exists(sessionId) ? "--resume" : "--session-id", sessionId.toString()));
        return argv;
    }

    private List<String> toolFlags(ToolSession session) {
        Path config = files.mcpConfig(session.serverUrl());
        String allowed = session.toolNames().stream()
                .map(name -> "mcp__" + ClaudeChatFiles.MCP_SERVER_NAME + "__" + name)
                .collect(Collectors.joining(","));
        return List.of("--mcp-config", config.toString(), "--allowedTools", allowed);
    }
}
