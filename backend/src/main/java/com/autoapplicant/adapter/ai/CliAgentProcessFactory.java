package com.autoapplicant.adapter.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds the sandboxed {@link ProcessBuilder} for a CLI-agent call. Crawled job-posting text
 * reaches the prompt, so a hostile posting can try to prompt-inject the agent. Three layers keep
 * that from reaching the backend's secrets:
 *
 * <ul>
 *   <li>the default command disables the agent's tools (see {@code app.ai.cli.command});
 *   <li>the process runs in a fresh empty temp directory, not the JVM's cwd where {@code .env}
 *       and {@code .secrets/} live;
 *   <li>the backend's own secrets are stripped from the child environment. PATH, HOME/USERPROFILE
 *       and the CLI's own auth variables (e.g. ANTHROPIC_API_KEY) are kept so it can still log in.
 * </ul>
 */
final class CliAgentProcessFactory {

    private static final String WORK_DIR_PREFIX = "cli-agent-";

    /** Backend secrets the agent never needs. Matched case-insensitively. */
    private static final Set<String> SECRET_NAMES = Set.of(
            "OPENAI_API_KEY",
            "GEMINI_API_KEY",
            "SECRET_ENCRYPTION_KEY",
            "DB_URL",
            "DB_USER",
            "DB_PASS",
            "POSTGRES_PASSWORD",
            "LINKEDIN_CLIENT_ID",
            "LINKEDIN_CLIENT_SECRET",
            "GOOGLE_APPLICATION_CREDENTIALS",
            "CAREERJET_AFFILIATE_ID");

    /** Catches secrets added later (e.g. SPRING_DATASOURCE_PASSWORD) without a list edit. */
    private static final Pattern SECRET_NAME_PATTERN =
            Pattern.compile(".*(PASSWORD|PASSWD|SECRET|CREDENTIALS|FIREBASE|DATASOURCE).*");

    private CliAgentProcessFactory() {}

    /** Creates a fresh, empty working directory for one agent call. */
    static Path createWorkDir() throws IOException {
        return Files.createTempDirectory(WORK_DIR_PREFIX);
    }

    /** A process builder for {@code command} confined to {@code workDir} with a scrubbed env. */
    static ProcessBuilder create(String command, Path workDir) {
        ProcessBuilder pb = new ProcessBuilder(parseCommand(command));
        pb.directory(workDir.toFile());
        scrubSecrets(pb.environment());
        return pb;
    }

    /** Splits the configured command on whitespace; the prompt goes to stdin, not argv. */
    static List<String> parseCommand(String command) {
        return Arrays.stream(command.trim().split("\\s+")).toList();
    }

    /** Removes every backend secret from {@code env} in place. */
    static void scrubSecrets(Map<String, String> env) {
        env.keySet().removeIf(CliAgentProcessFactory::isSecret);
    }

    private static boolean isSecret(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return SECRET_NAMES.contains(upper) || SECRET_NAME_PATTERN.matcher(upper).matches();
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
