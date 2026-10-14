package com.autoapplicant.adapter.ai;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one CLI-agent process: prompt on stdin, answer on stdout, bounded by a timeout. All three
 * pipes are serviced on their own threads so the timeout is measured by {@code waitFor} alone; a
 * blocking read on the caller's thread would only return once every holder of the pipe exited.
 */
final class CliAgentProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(CliAgentProcessRunner.class);

    /** How often the wait re-lists descendants, so ones the agent spawns are known before it exits. */
    private static final Duration DESCENDANT_POLL = Duration.ofMillis(200);

    /** How long a killed process tree gets to exit, so its files in the work dir are released. */
    private static final Duration KILL_WAIT = Duration.ofSeconds(5);

    /** How long the pipe threads get to finish once the process tree is gone. */
    private static final Duration DRAIN_WAIT = Duration.ofSeconds(2);

    record Result(int exitCode, String stdout, String stderr) {}

    /** The agent could not be started at all, as opposed to failing once running. */
    static final class LaunchException extends IOException {
        LaunchException(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }

    private CliAgentProcessRunner() {}

    /**
     * Starts {@code pb}, feeds it {@code stdin} and waits up to {@code timeout}. Whichever way it
     * ends, the process and every descendant seen while it ran are killed before this returns.
     *
     * @throws LaunchException the process could not be started
     * @throws TimeoutException the process outlived {@code timeout} and was killed
     * @throws IOException its output could not be read
     */
    static Result run(ProcessBuilder pb, String stdin, Duration timeout)
            throws IOException, InterruptedException, TimeoutException {
        Process proc = start(pb);
        Set<ProcessHandle> tree = new LinkedHashSet<>();
        Pipes pipes = Pipes.attach(proc, stdin);
        try {
            awaitExit(proc, timeout, tree);
        } finally {
            // Also after a normal exit: children it left behind would hold the pipes open.
            killAll(proc, tree);
            pipes.awaitStdinWriter();
        }
        return pipes.result(proc.exitValue());
    }

    /** The prompt being written and the output being drained, each on its own thread. */
    private record Pipes(Thread stdinWriter, CliAgentPipe stdout, CliAgentPipe stderr) {

        static Pipes attach(Process proc, String stdin) {
            return new Pipes(
                    startDaemon(() -> writeStdin(proc, stdin)),
                    CliAgentPipe.drain(proc.getInputStream()),
                    CliAgentPipe.drain(proc.getErrorStream()));
        }

        /** Bounded, so a writer stuck on a pipe nobody reads costs a parked daemon, not a hang. */
        void awaitStdinWriter() throws InterruptedException {
            stdinWriter.join(DRAIN_WAIT.toMillis());
            if (stdinWriter.isAlive()) {
                log.warn("CLI agent stdin writer still blocked after the process tree was killed");
            }
        }

        Result result(int exitCode) throws IOException, InterruptedException {
            return new Result(exitCode, stdout.text(DRAIN_WAIT), stderr.text(DRAIN_WAIT));
        }
    }

    static Thread startDaemon(Runnable work) {
        Thread thread = new Thread(work, "cli-agent-pipe");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static Process start(ProcessBuilder pb) throws LaunchException {
        try {
            return pb.start();
        } catch (IOException e) {
            throw new LaunchException(e);
        }
    }

    /**
     * Waits for exit while recording descendants. They have to be listed while the agent lives:
     * once it exits, Linux reparents its orphans and {@code descendants()} no longer finds them.
     */
    private static void awaitExit(Process proc, Duration timeout, Set<ProcessHandle> tree)
            throws InterruptedException, TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!proc.waitFor(DESCENDANT_POLL.toMillis(), TimeUnit.MILLISECONDS)) {
            proc.descendants().forEach(tree::add);
            if (System.nanoTime() - deadline >= 0) {
                throw new TimeoutException("no exit after " + timeout.toSeconds() + "s");
            }
        }
    }

    private static void writeStdin(Process proc, String stdin) {
        try (OutputStream os = proc.getOutputStream()) {
            os.write(stdin.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("CLI agent stdin closed early: {}", e.getMessage());
        }
    }

    /**
     * Force-kills the process, the descendants recorded earlier and any still attached, then waits
     * for them to exit. {@code destroyForcibly} alone neither reaches grandchildren nor waits, and
     * on Windows a live child keeps the work dir locked so it cannot be deleted.
     */
    private static void killAll(Process proc, Set<ProcessHandle> tree) throws InterruptedException {
        proc.descendants().forEach(tree::add);
        tree.add(proc.toHandle());
        tree.forEach(ProcessHandle::destroyForcibly);
        CompletableFuture<?>[] exits =
                tree.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(exits).get(KILL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("CLI agent process tree did not exit within {}s", KILL_WAIT.toSeconds());
        }
    }
}
