package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Runs {@link FakeClaudeChat} as a real child process in place of {@code claude}. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ClaudeChatAgentTest {

    private static final Duration GENEROUS = Duration.ofSeconds(30);
    private static final Duration SHORT = Duration.ofSeconds(3);
    private static final long EVENT_WAIT_SECONDS = 30;

    @TempDir Path appData;
    @TempDir Path claudeConfig;

    private final BlockingQueue<ChatEvent> events = new LinkedBlockingQueue<>();
    private final UUID session = UUID.randomUUID();

    @Test
    void a_new_chat_starts_its_session_and_a_used_one_resumes_it() throws Exception {
        String firstTurn = answerText(agent(GENEROUS, FakeClaudeChat.ECHO_ARGS).start(
                session, "Hej", events::add));
        assertThat(firstTurn).contains("--session-id " + session).doesNotContain("--resume");

        Path transcript = claudeConfig.resolve("projects").resolve("C--some-chat-dir");
        Files.createDirectories(transcript);
        Files.writeString(transcript.resolve(session + ".jsonl"), "{}\n");

        String secondTurn = answerText(agent(GENEROUS, FakeClaudeChat.ECHO_ARGS).start(
                session, "Igen", events::add));
        assertThat(secondTurn).contains("--resume " + session).doesNotContain("--session-id");
    }

    @Test
    void the_agent_runs_isolated_with_the_message_on_stdin() throws Exception {
        String args = answerText(agent(GENEROUS, FakeClaudeChat.ECHO_ARGS).start(
                session, "Mit spørgsmål", events::add));

        assertThat(args).contains("--tools= ", "--strict-mcp-config", "--setting-sources= ",
                "--disable-slash-commands", "--permission-mode dontAsk", "--model sonnet",
                "--output-format stream-json", "--include-partial-messages");
        assertThat(args).endsWith("| stdin:Mit spørgsmål");
        assertThat(args.substring(0, args.indexOf('|'))).doesNotContain("Mit spørgsmål");
    }

    @Test
    void pieces_of_the_answer_arrive_while_the_agent_is_still_writing() throws Exception {
        Path go = appData.resolve("go");
        agent(GENEROUS, FakeClaudeChat.STREAM_AFTER_GO, go.toString())
                .start(session, "Hej", events::add);

        assertThat(next()).isEqualTo(new ChatEvent.Delta(FakeClaudeChat.FIRST_PIECE));
        Files.createFile(go);
        assertThat(next()).isEqualTo(new ChatEvent.Delta(FakeClaudeChat.SECOND_PIECE));
        assertThat(next()).isEqualTo(new ChatEvent.Completed());
    }

    @Test
    void the_turn_ends_only_once_the_agent_has_exited() throws Exception {
        Set<ProcessHandle> already = children();
        agent(GENEROUS, FakeClaudeChat.RESULT_THEN_LINGER).start(session, "Hej", events::add);
        assertThat(next()).isInstanceOf(ChatEvent.Delta.class);
        Set<ProcessHandle> agent = children();
        agent.removeAll(already);

        assertThat(next()).isEqualTo(new ChatEvent.Completed());
        // Otherwise her next message could resume the session while this process still writes it.
        assertThat(agent).isNotEmpty().noneMatch(ProcessHandle::isAlive);
    }

    @Test
    void stop_kills_the_agent_and_ends_the_turn_as_stopped() throws Exception {
        Set<ProcessHandle> already = children();
        ChatAgentPort.RunningTurn turn = agent(GENEROUS, FakeClaudeChat.HANG_AFTER_DELTA)
                .start(session, "Hej", events::add);
        assertThat(next()).isInstanceOf(ChatEvent.Delta.class);
        Set<ProcessHandle> agent = children();
        agent.removeAll(already);

        turn.stop();

        assertThat(next()).isEqualTo(new ChatEvent.Stopped());
        assertThat(agent).isNotEmpty().noneMatch(ProcessHandle::isAlive);
        assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void an_agent_that_dies_mid_answer_fails_the_turn() throws Exception {
        agent(GENEROUS, FakeClaudeChat.DIE_MID_STREAM).start(session, "Hej", events::add);

        assertThat(next()).isInstanceOf(ChatEvent.Delta.class);
        assertThat(next()).isEqualTo(new ChatEvent.Failed(ChatFailure.FAILED));
    }

    @Test
    void a_turn_that_runs_too_long_is_killed_as_a_timeout() throws Exception {
        agent(SHORT, FakeClaudeChat.HANG_AFTER_DELTA).start(session, "Hej", events::add);

        assertThat(next()).isInstanceOf(ChatEvent.Delta.class);
        assertThat(next()).isEqualTo(new ChatEvent.Failed(ChatFailure.TIMEOUT));
    }

    @Test
    void a_missing_claude_is_reported_as_such() throws Exception {
        new ClaudeChatAgent(settings(List.of("no-such-claude-cli-for-tests"), GENEROUS))
                .start(session, "Hej", events::add);

        assertThat(next()).isEqualTo(new ChatEvent.Failed(ChatFailure.CLAUDE_MISSING));
    }

    private ClaudeChatAgent agent(Duration timeout, String... fakeArgs) {
        return new ClaudeChatAgent(settings(FakeClaudeChat.command(fakeArgs), timeout));
    }

    private ClaudeChatSettings settings(List<String> program, Duration timeout) {
        return new ClaudeChatSettings(program, appData.resolve("chat"), claudeConfig, "sonnet",
                timeout);
    }

    private ChatEvent next() throws InterruptedException {
        ChatEvent event = events.poll(EVENT_WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(event).as("an event within %ss", EVENT_WAIT_SECONDS).isNotNull();
        return event;
    }

    /** All the text of a turn that must complete. */
    private String answerText(ChatAgentPort.RunningTurn ignored) throws InterruptedException {
        List<String> pieces = new ArrayList<>();
        for (ChatEvent event = next(); !event.isTerminal(); event = next()) {
            pieces.add(((ChatEvent.Delta) event).text());
        }
        return String.join("", pieces);
    }

    /** Windows does not show other processes' arguments, so the agent is told apart by time. */
    private static Set<ProcessHandle> children() {
        return ProcessHandle.current().descendants().collect(Collectors.toCollection(HashSet::new));
    }
}
