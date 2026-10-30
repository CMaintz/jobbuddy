package com.autoapplicant.adapter.render;

import com.autoapplicant.adapter.render.TypstCommand.Format;
import com.autoapplicant.port.out.document.DocumentRenderException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs one {@code typst compile} and waits at most the configured wall-clock time. Output goes
 * to files and diagnostics to a file, so no pipe has to be drained while the process runs.
 */
@Component
class TypstCompiler {

    private static final Logger log = LoggerFactory.getLogger(TypstCompiler.class);

    /** How long a killed compiler gets to exit, so the workspace is no longer locked on Windows. */
    private static final Duration KILL_WAIT = Duration.ofSeconds(5);

    private final TypstCommand command;
    private final Duration timeout;

    @Autowired
    TypstCompiler(
            TypstAssets assets,
            @Value("${app.render.typst-path:typst}") String typstPath,
            @Value("${app.render.typst-timeout:PT10S}") Duration timeout) {
        this(new TypstCommand(typstPath, assets), timeout);
    }

    TypstCompiler(TypstCommand command, Duration timeout) {
        this.command = command;
        this.timeout = timeout;
    }

    /**
     * Compiles the workspace's main file to {@code output}.
     *
     * @throws DocumentRenderException Typst could not start, outran the timeout (and was killed)
     *     or exited non-zero; in the last case the exception carries its diagnostics
     */
    void compile(TypstWorkspace workspace, Format format, Path output) {
        Process process = start(command.build(workspace, format, output));
        awaitExit(process);
        if (process.exitValue() != 0) {
            String diagnostics = diagnostics(workspace);
            log.warn("Typst compile failed:\n{}", diagnostics);
            throw new DocumentRenderException("Typst compile failed", diagnostics);
        }
    }

    /** Waits up to the timeout; a process still running then, or on interrupt, is killed. */
    private void awaitExit(Process process) {
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                kill(process);
                throw new DocumentRenderException(
                        "Typst render timed out after " + timeout.toSeconds() + "s", "");
            }
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new DocumentRenderException("Typst render was interrupted", e);
        }
    }

    private static Process start(ProcessBuilder pb) {
        try {
            return pb.start();
        } catch (IOException e) {
            throw new DocumentRenderException("Could not start Typst (" + pb.command().get(0) + ")", e);
        }
    }

    /** Waits for the exit too: on Windows a live process keeps the workspace from being deleted. */
    private static void kill(Process process) {
        process.destroyForcibly();
        try {
            process.waitFor(KILL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String diagnostics(TypstWorkspace workspace) {
        try {
            return Files.readString(workspace.diagnosticsFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(diagnostics unavailable: " + e.getMessage() + ")";
        }
    }
}
