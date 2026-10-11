package com.autoapplicant.adapter.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds the {@link ProcessBuilder} for a CLI-agent call. Crawled job-posting text reaches the
 * prompt, so a hostile posting can try to prompt-inject the agent. What limits the damage:
 *
 * <ul>
 *   <li>the provider's default command removes or confines the agent's tools ({@link
 *       CliAgentCommands}). This is the control that actually stops it reading files or running
 *       commands, and an {@code app.ai.cli.command} override that drops those flags loses it;
 *   <li>the child environment is an allowlist ({@link CliAgentEnvironment}), so backend secrets
 *       such as DB passwords are not in the agent's env to read or echo back;
 *   <li>the process runs in a fresh empty temp directory rather than the JVM's cwd. That only
 *       keeps {@code .env}, {@code .secrets/} and project-scoped agent config out of reach by
 *       relative path; an agent with file tools could still open them by absolute path.
 * </ul>
 */
final class CliAgentProcessFactory {

    private static final String WORK_DIR_PREFIX = "cli-agent-";

    private static final int DELETE_ATTEMPTS = 5;

    private static final Duration DELETE_RETRY_DELAY = Duration.ofMillis(200);

    private CliAgentProcessFactory() {}

    /** Creates a fresh, empty working directory for one agent call. */
    static Path createWorkDir() throws IOException {
        return Files.createTempDirectory(WORK_DIR_PREFIX);
    }

    /**
     * A process builder for {@code command} run in {@code workDir}, with the env allowlisted for
     * {@code provider} and the program resolved against that env's PATH.
     */
    static ProcessBuilder create(String command, Path workDir, String provider) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(workDir.toFile());
        CliAgentEnvironment.restrict(pb.environment(), provider);
        pb.command(CliAgentExecutable.resolve(
                parseCommand(command), pb.environment(), CliAgentExecutable.isWindows()));
        return pb;
    }

    /** Splits the configured command on whitespace; the prompt goes to stdin, not argv. */
    static List<String> parseCommand(String command) {
        return Arrays.stream(command.trim().split("\\s+")).toList();
    }

    /**
     * Deletes the working directory and anything the agent left in it, retrying while a dying
     * process still holds a file open (Windows refuses to delete those). If it still fails, the
     * leftovers are queued for deletion at JVM exit and the failure is rethrown.
     *
     * @throws UncheckedIOException the directory could not be deleted now
     */
    static void deleteWorkDir(Path workDir) {
        for (int attempt = 1; ; attempt++) {
            try {
                deleteTree(workDir);
                return;
            } catch (UncheckedIOException e) {
                if (attempt == DELETE_ATTEMPTS || !pauseBeforeRetry()) {
                    deleteOnExit(workDir);
                    throw e;
                }
            }
        }
    }

    private static void deleteTree(Path workDir) {
        if (Files.notExists(workDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(CliAgentProcessFactory::deletePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** False when interrupted, so the caller stops retrying. */
    private static boolean pauseBeforeRetry() {
        try {
            Thread.sleep(DELETE_RETRY_DELAY.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Registers parents before children: deleteOnExit runs in reverse, so children go first. */
    private static void deleteOnExit(Path workDir) {
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.forEach(path -> path.toFile().deleteOnExit());
        } catch (IOException | UncheckedIOException e) {
            workDir.toFile().deleteOnExit();
        }
    }
}
