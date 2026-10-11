package com.autoapplicant.adapter.ai;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** A stand-in agent launched as a real child JVM by {@link CliAgentProcessRunnerTest}. */
public final class FakeCliAgent {

    static final String ECHO = "echo";
    static final String FAIL = "fail";
    static final String HANG = "hang";
    static final String SPAWN_AND_HANG = "spawn-and-hang";
    static final String ANSWER_AND_ORPHAN = "answer-and-orphan";
    static final String ANSWER = "answer";

    static final int FAIL_EXIT_CODE = 3;

    private static final long HANG_MILLIS = 120_000;

    /** Long enough for the runner to see the orphan while this process is still its parent. */
    private static final long LINGER_MILLIS = 1_500;

    private FakeCliAgent() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        switch (args[0]) {
            case ECHO -> System.out.print(
                    "answer:" + new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
            case FAIL -> {
                System.err.print("bad prompt");
                System.exit(FAIL_EXIT_CODE);
            }
            case HANG -> Thread.sleep(HANG_MILLIS);
            case SPAWN_AND_HANG -> {
                Process child = new ProcessBuilder(command(HANG)).inheritIO().start();
                Files.writeString(Path.of(args[1]), Long.toString(child.pid()));
                Thread.sleep(HANG_MILLIS);
            }
            case ANSWER_AND_ORPHAN -> {
                Process child = new ProcessBuilder(command(HANG)).inheritIO().start();
                Files.writeString(Path.of(args[1]), Long.toString(child.pid()));
                System.out.print(ANSWER);
                System.out.flush();
                Thread.sleep(LINGER_MILLIS);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    /** The command line that runs this class in mode {@code args[0]} in a fresh JVM. */
    static List<String> command(String... args) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> head = List.of(java, "-cp", codeLocation(), FakeCliAgent.class.getName());
        return Stream.concat(head.stream(), List.of(args).stream()).toList();
    }

    private static String codeLocation() {
        try {
            return Path.of(FakeCliAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
