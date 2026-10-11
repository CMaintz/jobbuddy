package com.autoapplicant.adapter.ai;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Resolves the agent's executable the way a Windows shell would. {@link ProcessBuilder} only
 * tries {@code .exe}, but npm installs {@code claude} and {@code codex} as {@code .cmd} shims, so
 * a bare {@code codex} would not start. Elsewhere the OS search already works and argv is kept.
 */
final class CliAgentExecutable {

    private static final String DEFAULT_PATHEXT = ".COM;.EXE;.BAT;.CMD";

    private static final String PATHEXT_SEPARATOR = ";";

    /** Java hands a batch file to cmd.exe, which parses these even inside an argument. */
    private static final Pattern CMD_METACHARACTERS = Pattern.compile("[&|<>^%!\"()\\r\\n]");

    private static final Pattern BATCH_FILE = Pattern.compile("(?i).*\\.(cmd|bat)$");

    private CliAgentExecutable() {}

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * {@code argv} with its program replaced by the full path PATH/PATHEXT lookup finds in
     * {@code env}, or unchanged when not on Windows or nothing matches (the launch then fails).
     *
     * @throws IllegalArgumentException a batch-file program was given an argument cmd.exe would
     *     interpret, which could run a second command
     */
    static List<String> resolve(List<String> argv, Map<String, String> env, boolean windows) {
        if (!windows) {
            return argv;
        }
        String program = findOnPath(argv.get(0), env).orElse(argv.get(0));
        if (BATCH_FILE.matcher(program).matches()) {
            argv.stream().skip(1).forEach(CliAgentExecutable::requireSafeBatchArgument);
        }
        List<String> resolved = new ArrayList<>(argv);
        resolved.set(0, program);
        return resolved;
    }

    private static Optional<String> findOnPath(String program, Map<String, String> env) {
        if (program.contains("/") || program.contains("\\")) {
            return Optional.empty();
        }
        List<String> extensions = Arrays.asList(
                lookup(env, "PATHEXT").orElse(DEFAULT_PATHEXT).split(PATHEXT_SEPARATOR));
        return Arrays.stream(lookup(env, "PATH").orElse("").split(File.pathSeparator))
                .filter(dir -> !dir.isBlank())
                .flatMap(dir -> extensions.stream().flatMap(ext -> candidate(dir, program + ext)))
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .findFirst();
    }

    /** A malformed PATH entry is skipped, as a shell would. */
    private static Stream<Path> candidate(String dir, String fileName) {
        try {
            return Stream.of(Path.of(dir, fileName));
        } catch (InvalidPathException e) {
            return Stream.empty();
        }
    }

    private static Optional<String> lookup(Map<String, String> env, String name) {
        return env.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static void requireSafeBatchArgument(String arg) {
        if (CMD_METACHARACTERS.matcher(arg).find()) {
            throw new IllegalArgumentException("CLI agent argument '" + arg + "' contains a"
                    + " character cmd.exe would interpret; the agent is a batch file");
        }
    }
}
