package com.autoapplicant.adapter.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one CLI-agent process: prompt on stdin, answer on stdout, bounded by a timeout. All three
 * pipes are serviced on their own threads so the timeout is measured by {@code waitFor} alone; a
 * blocking read on the caller's thread would only return once every holder of the pipe exited.
 */
final class CliAgentProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(CliAgentProcessRunner.class);

    /** How long a killed process tree gets to exit, so its files in the work dir are released. */
    private static final Duration KILL_WAIT = Duration.ofSeconds(5);

    /** How long the pipe threads get to finish once the process tree is gone. */
    private static final Duration DRAIN_WAIT = Duration.ofSeconds(2);

    record Result(int exitCode, String stdout, String stderr) {}

    private CliAgentProcessRunner() {}

    /**
     * Starts {@code pb}, feeds it {@code stdin} and waits up to {@code timeout}. Whichever way it
     * ends, the process and every descendant are gone when this returns or throws.
     *
     * @throws TimeoutException the process outlived {@code timeout} and was killed
     * @throws IOException the process could not be started or its output could not be read
     */
    static Result run(ProcessBuilder pb, String stdin, Duration timeout)
            throws IOException, InterruptedException, TimeoutException {
        Process proc = pb.start();
        Pipes pipes;
        try {
            pipes = attachPipes(proc, stdin);
            awaitExit(proc, timeout);
        } finally {
            // Also after a normal exit: children it left behind would hold stdout open.
            killTree(proc);
        }
        return pipes.result(proc.exitValue());
    }

    /** stdout and stderr being collected in the background. */
    private record Pipes(FutureTask<String> stdout, FutureTask<String> stderr) {

        Result result(int exitCode) throws IOException, InterruptedException {
            return new Result(exitCode, await(stdout), await(stderr));
        }
    }

    private static Pipes attachPipes(Process proc, String stdin) {
        startDaemon(() -> writeStdin(proc, stdin));
        return new Pipes(collect(proc.getInputStream()), collect(proc.getErrorStream()));
    }

    private static void awaitExit(Process proc, Duration timeout)
            throws InterruptedException, TimeoutException {
        if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("no exit after " + timeout.toSeconds() + "s");
        }
    }

    private static void writeStdin(Process proc, String stdin) {
        try (OutputStream os = proc.getOutputStream()) {
            os.write(stdin.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("CLI agent stdin closed early: {}", e.getMessage());
        }
    }

    private static FutureTask<String> collect(InputStream in) {
        FutureTask<String> task =
                new FutureTask<>(() -> new String(in.readAllBytes(), StandardCharsets.UTF_8));
        startDaemon(task);
        return task;
    }

    private static void startDaemon(Runnable work) {
        Thread thread = new Thread(work, "cli-agent-pipe");
        thread.setDaemon(true);
        thread.start();
    }

    private static String await(FutureTask<String> output)
            throws IOException, InterruptedException {
        try {
            return output.get(DRAIN_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IOException("Could not read CLI agent output", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("CLI agent output did not close after the process exited", e);
        }
    }

    /**
     * Force-kills the process and its descendants, then waits for them to exit.
     * {@code destroyForcibly} alone neither reaches grandchildren nor waits, and on Windows a
     * live child keeps the work dir locked so it cannot be deleted.
     */
    private static void killTree(Process proc) throws InterruptedException {
        List<ProcessHandle> tree =
                Stream.concat(proc.descendants(), Stream.of(proc.toHandle())).toList();
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
