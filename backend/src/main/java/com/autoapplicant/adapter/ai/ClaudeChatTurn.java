package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.port.out.chat.ChatAgentPort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One chat turn's {@code claude} process. Its stdout is relayed line by line as it arrives, not
 * collected at exit as {@link CliAgentProcessRunner} does, so the answer streams. A watcher thread
 * enforces the timeout and, whichever way the turn ends, kills the whole process tree.
 *
 * <p>The listener hears exactly one terminal event and nothing after it: a stop or the timeout
 * as soon as they happen, else the result line's verdict once the process tree has exited.
 */
final class ClaudeChatTurn implements ChatAgentPort.RunningTurn {

    private static final Logger log = LoggerFactory.getLogger(ClaudeChatTurn.class);

    private static final Duration WATCH_POLL = Duration.ofMillis(200);

    /** How long the relay waits for the exit code once stdout has closed. */
    private static final Duration EXIT_WAIT = Duration.ofSeconds(5);

    /** Enough of stderr to diagnose a failure without filling the log. */
    private static final int STDERR_LOG_CHARS = 500;

    private final Consumer<ChatEvent> listener;
    private final ClaudeStreamParser parser = new ClaudeStreamParser();
    private boolean ended;
    private CliAgentProcessTree tree;

    /** Set before the tree is killed on purpose, so the relay reports why stdout closed. */
    private volatile ChatEvent endedBy;

    /** A turn not started yet, so tool access can be opened for it before its process starts. */
    ClaudeChatTurn(Consumer<ChatEvent> listener) {
        this.listener = listener;
    }

    /** Starts {@code pb} with {@code message} on its stdin and returns at once. */
    void launch(ProcessBuilder pb, String message, Duration timeout) {
        try {
            run(CliAgentProcessRunner.start(pb), message, timeout);
        } catch (CliAgentProcessRunner.LaunchException e) {
            log.warn("Could not start the chat agent: {}", e.getMessage());
            fail(ChatFailure.CLAUDE_MISSING);
        }
    }

    /** Ends a turn that never started. */
    void fail(ChatFailure failure) {
        emit(new ChatEvent.Failed(failure));
    }

    /**
     * Passes a checked button on to her, in order with the answer's text. One offered after the
     * turn has ended (a tool call still in flight when it was stopped) is dropped.
     */
    void offer(ChatAction action) {
        emit(new ChatEvent.Action(action));
    }

    /**
     * Shows her a stored proposal, in order with the answer's text. One stored after the turn has
     * ended is not shown and stays waiting with nothing to approve it from; it changes nothing.
     */
    void propose(ChatPlan plan) {
        emit(new ChatEvent.Proposed(plan));
    }

    /** Feeds the message, relays the output and watches the clock, each on its own thread. */
    private void run(Process proc, String message, Duration timeout) {
        tree = new CliAgentProcessTree(proc);
        CliAgentProcessRunner.startDaemon(() -> CliAgentProcessRunner.writeStdin(proc, message));
        CliAgentPipe stderr = CliAgentPipe.drain(proc.getErrorStream());
        CliAgentProcessRunner.startDaemon(() -> relay(proc, stderr));
        CliAgentProcessRunner.startDaemon(() -> watch(proc, timeout));
    }

    @Override
    public void stop() {
        end(new ChatEvent.Stopped());
    }

    /**
     * Passes the answer on as it arrives but holds the result line's verdict until the process
     * tree is gone: Claude Code still writes the session file after printing its result, and the
     * next turn resumes that file as soon as this one is reported over.
     */
    /**
     * Whatever goes wrong here, the turn still ends with a terminal event: that event is what
     * frees the chat, its slot and its tool access, so a relay that died silently would hold
     * them until a restart.
     */
    private void relay(Process proc, CliAgentPipe stderr) {
        ChatEvent verdict = new ChatEvent.Failed(ChatFailure.FAILED);
        try {
            verdict = readAnswer(proc).orElseGet(parser::atExit);
            awaitExit(proc);
        } catch (RuntimeException e) {
            log.warn("Chat agent relay failed", e);
        } finally {
            killTree();
            finish(verdict, proc, stderr);
        }
    }

    private void finish(ChatEvent verdict, Process proc, CliAgentPipe stderr) {
        ChatEvent reason = endedBy;
        if (emit(reason != null ? reason : verdict) && reason == null
                && verdict instanceof ChatEvent.Failed) {
            logFailure(proc, stderr);
        }
    }

    /** Emits the answer's pieces until stdout closes; returns the result line's verdict, if any. */
    private Optional<ChatEvent> readAnswer(Process proc) {
        Optional<ChatEvent> verdict = Optional.empty();
        try (BufferedReader out = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line = out.readLine(); line != null; line = out.readLine()) {
                Optional<ChatEvent> lineVerdict = relayLine(line);
                if (lineVerdict.isPresent()) {
                    verdict = lineVerdict;
                }
            }
        } catch (IOException e) {
            log.debug("Chat agent output closed: {}", e.getMessage());
        }
        return verdict;
    }

    /** Emits a piece of the answer; a verdict is returned for later instead. */
    private Optional<ChatEvent> relayLine(String line) {
        Optional<ChatEvent> event = parser.parse(line);
        if (event.isPresent() && event.get().isTerminal()) {
            return event;
        }
        event.ifPresent(this::emit);
        return Optional.empty();
    }

    private static void awaitExit(Process proc) {
        try {
            proc.waitFor(EXIT_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void watch(Process proc, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!proc.waitFor(WATCH_POLL.toMillis(), TimeUnit.MILLISECONDS)) {
                tree.recordDescendants();
                if (System.nanoTime() - deadline >= 0) {
                    end(new ChatEvent.Failed(ChatFailure.TIMEOUT));
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Also after a normal exit: a child left behind would hold stdout open.
        killTree();
    }

    /**
     * Kills the tree before reporting {@code reason}, so whoever hears it can rely on the process
     * being gone (its working directory released, its session file no longer written).
     */
    private void end(ChatEvent reason) {
        endedBy = reason;
        killTree();
        emit(reason);
    }

    /** Passes {@code event} on unless the turn has ended; true if it did. */
    private synchronized boolean emit(ChatEvent event) {
        if (ended) {
            return false;
        }
        ended = event.isTerminal();
        try {
            listener.accept(event);
        } catch (RuntimeException e) {
            // The listener is the use case and the open stream; neither may stop the relay.
            log.warn("Chat event listener failed on {}", event.getClass().getSimpleName(), e);
        }
        return true;
    }

    private void killTree() {
        if (tree == null) {
            return;
        }
        try {
            tree.killAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void logFailure(Process proc, CliAgentPipe stderr) {
        try {
            String exit = proc.waitFor(EXIT_WAIT.toMillis(), TimeUnit.MILLISECONDS)
                    ? Integer.toString(proc.exitValue()) : "none";
            String err = stderr.text(EXIT_WAIT).strip();
            log.warn("Chat agent ended without an answer (exit {}): {}", exit,
                    err.length() > STDERR_LOG_CHARS ? err.substring(0, STDERR_LOG_CHARS) : err);
        } catch (IOException e) {
            log.warn("Chat agent ended without an answer; stderr unreadable: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
