package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.render.TypstCommand.Format;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** A burst of renders runs a bounded number of Typst processes; the rest wait their turn. */
class TypstCompilerLimitTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final long WAIT_MS = 5_000;

    /**
     * Bug caught: the limit is not enforced (or the slot is taken after the process starts), so
     * every concurrent export spawns its own all-core Typst process and the container runs out of
     * memory.
     */
    @Test
    void renderPastTheLimitWaitsUntilASlotFrees() throws Exception {
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger spawned = new AtomicInteger();
        TypstCompiler compiler = new TypstCompiler(
                new TypstCommand("typst", TypstFixtures.assets(), 1, Map.of()), TIMEOUT, 1,
                pb -> {
                    spawned.incrementAndGet();
                    return new BlockingProcess(finish);
                });

        try (TypstWorkspace first = TypstWorkspace.create(); TypstWorkspace second = TypstWorkspace.create()) {
            Thread a = render(compiler, first);
            awaitTrue(() -> spawned.get() == 1);
            Thread b = render(compiler, second);
            awaitTrue(() -> b.getState() == Thread.State.WAITING || b.getState() == Thread.State.TIMED_WAITING);

            assertThat(spawned).as("the second render started while the first held the only slot")
                    .hasValue(1);

            finish.countDown();
            a.join(WAIT_MS);
            b.join(WAIT_MS);
            assertThat(spawned).hasValue(2);
        }
    }

    private static Thread render(TypstCompiler compiler, TypstWorkspace workspace) {
        Thread thread = new Thread(() ->
                compiler.compile(workspace, Format.PDF, workspace.outputDir().resolve("out.pdf")));
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition not reached in time").isLessThan(deadline);
            Thread.sleep(10);
        }
    }

    /** A Typst process that runs until the test lets it exit cleanly. */
    private static final class BlockingProcess extends Process {

        private final CountDownLatch finish;

        BlockingProcess(CountDownLatch finish) {
            this.finish = finish;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            return finish.await(timeout, unit);
        }

        @Override
        public int waitFor() throws InterruptedException {
            finish.await();
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            finish.countDown();
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }
    }
}
