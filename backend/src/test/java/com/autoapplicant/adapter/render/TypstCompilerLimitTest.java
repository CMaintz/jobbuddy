package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.autoapplicant.adapter.render.TypstCommand.Format;
import com.autoapplicant.port.out.document.DocumentRenderException;
import com.autoapplicant.port.out.document.DocumentRendererBusyException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * A burst of renders runs a bounded number of Typst processes; the rest wait their turn, but
 * never so long that request threads pile up behind the renderer.
 */
class TypstCompilerLimitTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SHORT_QUEUE_WAIT = Duration.ofMillis(300);
    private static final long WAIT_MS = 5_000;

    private final CountDownLatch finish = new CountDownLatch(1);
    private final AtomicInteger spawned = new AtomicInteger();

    /**
     * Bug caught: the limit is not enforced (or the slot is taken after the process starts), so
     * every concurrent export spawns its own all-core Typst process and the container runs out of
     * memory.
     */
    @Test
    void renderPastTheLimitWaitsUntilASlotFrees() throws Exception {
        TypstCompiler compiler = compiler(TIMEOUT, this::blocking);

        try (TypstWorkspace first = TypstWorkspace.create(); TypstWorkspace second = TypstWorkspace.create()) {
            Thread a = render(compiler, first, new AtomicReference<>());
            awaitTrue(() -> spawned.get() == 1);
            Thread b = render(compiler, second, new AtomicReference<>());
            awaitParked(b);

            assertThat(spawned).as("the second render started while the first held the only slot")
                    .hasValue(1);

            finish.countDown();
            a.join(WAIT_MS);
            b.join(WAIT_MS);
            assertThat(spawned).hasValue(2);
        }
    }

    /**
     * Bug caught: the wait for a slot is unbounded, so a burst of exports parks every request
     * thread behind the renderer and the whole backend stops answering.
     */
    @Test
    void renderThatGetsNoSlotInTimeFailsAsBusy() throws Exception {
        TypstCompiler compiler = compiler(SHORT_QUEUE_WAIT, this::blocking);

        try (TypstWorkspace first = TypstWorkspace.create(); TypstWorkspace second = TypstWorkspace.create()) {
            Thread a = render(compiler, first, new AtomicReference<>());
            awaitTrue(() -> spawned.get() == 1);

            assertTimeoutPreemptively(Duration.ofMillis(WAIT_MS), () ->
                    assertThatThrownBy(() -> compile(compiler, second))
                            .isInstanceOf(DocumentRendererBusyException.class));
            assertThat(spawned).hasValue(1);

            finish.countDown();
            a.join(WAIT_MS);
        }
    }

    /**
     * Bug caught: a render that fails to start or exits non-zero keeps its slot, so after a
     * couple of broken templates every later render is refused as busy.
     */
    @Test
    void failedRendersGiveTheirSlotBack() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TypstCompiler compiler = compiler(SHORT_QUEUE_WAIT, pb -> switch (calls.incrementAndGet()) {
            case 1 -> throw new IOException("typst not found");
            case 2 -> new ExitedProcess(1);
            default -> new ExitedProcess(0);
        });

        try (TypstWorkspace workspace = TypstWorkspace.create()) {
            assertThatThrownBy(() -> compile(compiler, workspace)).hasMessageContaining("Could not start");
            assertThatThrownBy(() -> compile(compiler, workspace)).hasMessageContaining("compile failed");

            compile(compiler, workspace);
            assertThat(calls).hasValue(3);
        }
    }

    /**
     * Bug caught: a render interrupted while queued releases a slot it never took, so the limit
     * silently grows by one with every cancelled request.
     */
    @Test
    void renderInterruptedWhileQueuedDoesNotWidenTheLimit() throws Exception {
        TypstCompiler compiler = compiler(TIMEOUT, this::blocking);

        try (TypstWorkspace first = TypstWorkspace.create(); TypstWorkspace second = TypstWorkspace.create();
             TypstWorkspace third = TypstWorkspace.create()) {
            Thread a = render(compiler, first, new AtomicReference<>());
            awaitTrue(() -> spawned.get() == 1);
            AtomicReference<Throwable> queuedFailure = new AtomicReference<>();
            Thread b = render(compiler, second, queuedFailure);
            awaitParked(b);
            b.interrupt();
            b.join(WAIT_MS);
            assertThat(queuedFailure.get()).hasMessageContaining("interrupted");

            Thread c = render(compiler, third, new AtomicReference<>());
            awaitParked(c);
            assertThat(spawned).as("a render started while the first still held the only slot")
                    .hasValue(1);

            finish.countDown();
            a.join(WAIT_MS);
            c.join(WAIT_MS);
        }
    }

    /** Bug caught: a limit of 0 starts fine and then refuses or hangs every render. */
    @Test
    void limitsBelowOneAreRefusedAtStartup() {
        assertThatThrownBy(() -> new TypstCompiler(command(1), TIMEOUT, 0, TIMEOUT, this::blocking))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typst-max-concurrent");
        assertThatThrownBy(() -> command(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typst-jobs");
    }

    private TypstCompiler compiler(Duration queueWait, TypstCompiler.Launcher launcher) {
        return new TypstCompiler(command(1), TIMEOUT, 1, queueWait, launcher);
    }

    private static TypstCommand command(int jobs) {
        return new TypstCommand("typst", TypstFixtures.assets(), jobs, Map.of());
    }

    private Process blocking(ProcessBuilder pb) {
        spawned.incrementAndGet();
        return new BlockingProcess(finish);
    }

    private static void compile(TypstCompiler compiler, TypstWorkspace workspace) {
        compiler.compile(workspace, Format.PDF, workspace.outputDir().resolve("out.pdf"));
    }

    private static Thread render(TypstCompiler compiler, TypstWorkspace workspace,
                                 AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                compile(compiler, workspace);
            } catch (DocumentRenderException e) {
                failure.set(e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** A queued render and a running one both park, so only the spawn count tells them apart. */
    private static void awaitParked(Thread thread) throws InterruptedException {
        awaitTrue(() -> thread.getState() == Thread.State.WAITING
                || thread.getState() == Thread.State.TIMED_WAITING);
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition not reached in time").isLessThan(deadline);
            Thread.sleep(10);
        }
    }

    /** A Typst process that runs until the test lets it exit cleanly. */
    private static final class BlockingProcess extends StubProcess {

        private final CountDownLatch finish;

        BlockingProcess(CountDownLatch finish) {
            this.finish = finish;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            return finish.await(timeout, unit);
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            finish.countDown();
        }
    }

    /** A Typst process that has already exited with {@code exitValue}. */
    private static final class ExitedProcess extends StubProcess {

        private final int exitValue;

        ExitedProcess(int exitValue) {
            this.exitValue = exitValue;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return exitValue;
        }

        @Override
        public void destroy() {
            // Nothing to stop: the process has already exited.
        }
    }

    private abstract static class StubProcess extends Process {

        @Override
        public int waitFor() throws InterruptedException {
            waitFor(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            return exitValue();
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
