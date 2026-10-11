package com.autoapplicant.adapter.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private CliAgentProcessFactory() {}

    /** Creates a fresh, empty working directory for one agent call. */
    static Path createWorkDir() throws IOException {
        return Files.createTempDirectory(WORK_DIR_PREFIX);
    }

    /** A process builder for {@code command} run in {@code workDir} with an allowlisted env. */
    static ProcessBuilder create(String command, Path workDir) {
        List<String> argv = parseCommand(command);
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(workDir.toFile());
        CliAgentEnvironment.restrict(pb.environment(), argv.get(0));
        return pb;
    }

    /** Splits the configured command on whitespace; the prompt goes to stdin, not argv. */
    static List<String> parseCommand(String command) {
        return Arrays.stream(command.trim().split("\\s+")).toList();
    }

    /** Deletes the working directory and anything the agent left in it. */
    static void deleteWorkDir(Path workDir) {
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
}
