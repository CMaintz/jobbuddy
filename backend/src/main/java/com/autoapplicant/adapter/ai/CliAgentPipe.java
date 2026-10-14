package com.autoapplicant.adapter.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One of the agent's output pipes, read to EOF on a daemon thread. What has arrived stays readable
 * even if EOF never comes: a descendant the kill could not reach (an orphan reparented away from
 * the agent) may hold the pipe open long after the agent itself exited with a complete answer.
 */
final class CliAgentPipe {

    private static final Logger log = LoggerFactory.getLogger(CliAgentPipe.class);

    private static final int READ_CHUNK_BYTES = 8192;

    private final InputStream in;
    private final ByteArrayOutputStream received = new ByteArrayOutputStream();
    private final CountDownLatch closed = new CountDownLatch(1);
    private volatile IOException failure;

    private CliAgentPipe(InputStream in) {
        this.in = in;
    }

    /** Starts draining {@code in} in the background. */
    static CliAgentPipe drain(InputStream in) {
        CliAgentPipe pipe = new CliAgentPipe(in);
        CliAgentProcessRunner.startDaemon(pipe::readToEnd);
        return pipe;
    }

    /**
     * The text received, waiting up to {@code wait} for EOF. Without EOF by then it returns what
     * has arrived and logs a warning.
     *
     * @throws IOException reading the pipe failed
     */
    String text(Duration wait) throws IOException, InterruptedException {
        if (!closed.await(wait.toMillis(), TimeUnit.MILLISECONDS)) {
            log.warn("CLI agent output still open {}s after exit; using what arrived",
                    wait.toSeconds());
        }
        if (failure != null) {
            throw failure;
        }
        synchronized (received) {
            return received.toString(StandardCharsets.UTF_8);
        }
    }

    private void readToEnd() {
        byte[] chunk = new byte[READ_CHUNK_BYTES];
        try (InputStream stream = in) {
            for (int n = stream.read(chunk); n >= 0; n = stream.read(chunk)) {
                synchronized (received) {
                    received.write(chunk, 0, n);
                }
            }
        } catch (IOException e) {
            failure = e;
        } finally {
            closed.countDown();
        }
    }
}
