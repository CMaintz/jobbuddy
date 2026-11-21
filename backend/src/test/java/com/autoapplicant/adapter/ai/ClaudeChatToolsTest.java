package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.port.out.chat.ChatToolsPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** How a turn reaches JobBuddy's MCP server, with {@link FakeClaudeChat} in place of claude. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ClaudeChatToolsTest {

    private static final long EVENT_WAIT_SECONDS = 30;
    private static final ChatAction PIPELINE = new ChatAction(ChatAction.Kind.SHOW_PIPELINE, List.of());

    @TempDir Path appData;
    @TempDir Path claudeConfig;

    private final UUID her = UUID.randomUUID();
    private final BlockingQueue<ChatEvent> events = new LinkedBlockingQueue<>();
    private final FakeToolAccess tools = new FakeToolAccess("turn-" + "ab".repeat(16));

    @Test
    void the_turn_gets_only_jobbuddys_tools_and_its_token_only_through_the_environment()
            throws Exception {
        String args = answer(agent(FakeClaudeChat.ECHO_ARGS).start(her, UUID.randomUUID(), "Hej",
                events::add));
        String argv = args.substring(0, args.indexOf('|'));
        Path config = appData.resolve("chat").resolve("mcp.json");

        assertThat(tools.openedFor).containsExactly(her);
        assertThat(argv).contains("--mcp-config " + config, "--strict-mcp-config", "--tools= ",
                "--allowedTools mcp__jobbuddy__search_postings,mcp__jobbuddy__open_in_app ");
        assertThat(argv).doesNotContain(tools.token);
        assertThat(args).endsWith("| env:" + tools.token);
        assertThat(Files.readString(config)).contains("http://127.0.0.1:18080/mcp",
                "Bearer ${JOBBUDDY_MCP_TOKEN}").doesNotContain(tools.token);
    }

    @Test
    void the_token_dies_with_the_turn() throws Exception {
        answer(agent(FakeClaudeChat.ECHO_ARGS).start(her, UUID.randomUUID(), "Hej", events::add));

        assertThat(tools.closed).isTrue();
    }

    @Test
    void a_button_reaches_her_during_the_turn_and_never_after_it() throws Exception {
        Path go = appData.resolve("go");
        agent(FakeClaudeChat.STREAM_AFTER_GO, go.toString())
                .start(her, UUID.randomUUID(), "Hej", events::add);
        assertThat(next()).isInstanceOf(ChatEvent.Delta.class);

        tools.offer.accept(PIPELINE);
        assertThat(next()).isEqualTo(new ChatEvent.Action(PIPELINE));
        Files.createFile(go);
        assertThat(next()).isInstanceOf(ChatEvent.Delta.class);
        assertThat(next()).isEqualTo(new ChatEvent.Completed());

        tools.offer.accept(PIPELINE);
        assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();
    }

    private ClaudeChatAgent agent(String... fakeArgs) {
        return new ClaudeChatAgent(new ClaudeChatSettings(FakeClaudeChat.command(fakeArgs),
                appData.resolve("chat"), claudeConfig, "sonnet", Duration.ofSeconds(30)), tools);
    }

    private ChatEvent next() throws InterruptedException {
        ChatEvent event = events.poll(EVENT_WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(event).as("an event within %ss", EVENT_WAIT_SECONDS).isNotNull();
        return event;
    }

    private String answer(Object ignored) throws InterruptedException {
        List<String> pieces = new ArrayList<>();
        for (ChatEvent event = next(); !event.isTerminal(); event = next()) {
            if (event instanceof ChatEvent.Delta delta) {
                pieces.add(delta.text());
            }
        }
        return String.join("", pieces);
    }

    private static final class FakeToolAccess implements ChatToolsPort {

        final String token;
        final List<UUID> openedFor = new ArrayList<>();
        volatile Consumer<ChatAction> offer;
        volatile boolean closed;

        FakeToolAccess(String token) {
            this.token = token;
        }

        @Override
        public ToolSession open(UUID userId, Consumer<ChatAction> actions) {
            openedFor.add(userId);
            offer = actions;
            return new ToolSession("http://127.0.0.1:18080/mcp", token,
                    List.of("search_postings", "open_in_app"), () -> closed = true);
        }
    }
}
