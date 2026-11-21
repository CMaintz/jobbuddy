package com.autoapplicant.adapter.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * A stand-in for {@code claude -p --output-format stream-json}, launched as a real child JVM by
 * {@link ClaudeChatAgentTest}. Its first argument picks the behaviour; the agent appends its own
 * flags after the rest.
 */
public final class FakeClaudeChat {

    /** Writes one piece, then the rest only once the file named by the next argument exists. */
    static final String STREAM_AFTER_GO = "stream-after-go";
    /** Writes one piece and never finishes. */
    static final String HANG_AFTER_DELTA = "hang-after-delta";
    /** Writes one piece and exits with an error, without a result line. */
    static final String DIE_MID_STREAM = "die-mid-stream";
    /** Writes a whole answer, then takes a while to exit, as claude does closing its servers. */
    static final String RESULT_THEN_LINGER = "result-then-linger";
    /** Answers with its own arguments and the prompt it read. */
    static final String ECHO_ARGS = "echo-args";

    static final String FIRST_PIECE = "Hej ";
    static final String SECOND_PIECE = "med dig";

    private static final long HANG_MILLIS = 120_000;
    private static final long POLL_MILLIS = 50;
    private static final long LINGER_MILLIS = 2_000;

    private FakeClaudeChat() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        String mode = args[0];
        if (ECHO_ARGS.equals(mode)) {
            String prompt = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            String echoed = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            print(ClaudeStreamLines.textDelta(escape(echoed + " | stdin:" + prompt)));
            print(ClaudeStreamLines.RESULT_OK);
            return;
        }
        print(ClaudeStreamLines.textDelta(FIRST_PIECE));
        switch (mode) {
            case STREAM_AFTER_GO -> {
                awaitFile(Path.of(args[1]));
                print(ClaudeStreamLines.textDelta(SECOND_PIECE));
                print(ClaudeStreamLines.RESULT_OK);
            }
            case HANG_AFTER_DELTA -> Thread.sleep(HANG_MILLIS);
            case DIE_MID_STREAM -> System.exit(1);
            case RESULT_THEN_LINGER -> {
                print(ClaudeStreamLines.RESULT_OK);
                Thread.sleep(LINGER_MILLIS);
            }
            default -> throw new IllegalArgumentException(mode);
        }
    }

    /** UTF-8 whatever the platform's default, as Claude Code writes. */
    private static void print(String line) {
        byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        System.out.write(bytes, 0, bytes.length);
        System.out.flush();
    }

    private static void awaitFile(Path file) throws InterruptedException {
        while (!Files.exists(file)) {
            Thread.sleep(POLL_MILLIS);
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** The command line that runs this class in a fresh JVM, ahead of the agent's flags. */
    static List<String> command(String... args) {
        List<String> head = FakeCliAgent.command().stream()
                .map(part -> part.equals(FakeCliAgent.class.getName())
                        ? FakeClaudeChat.class.getName() : part)
                .toList();
        return Stream.concat(head.stream(), Stream.of(args)).toList();
    }
}
