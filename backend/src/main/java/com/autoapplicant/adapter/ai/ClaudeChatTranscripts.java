package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.domain.chat.ChatMessage.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The session files Claude Code keeps, one JSON object per line, at
 * {@code <config dir>/projects/<slug of the cwd>/<session id>.jsonl}. The file is found by its
 * session id rather than by recomputing the slug, whose rule is Claude Code's own. Only her
 * messages and the answers' text are shown: tool calls, tool results, thinking, subagent
 * conversations and Claude Code's bookkeeping lines are not.
 */
final class ClaudeChatTranscripts {

    private static final String PROJECTS = "projects";
    private static final String EXTENSION = ".jsonl";
    private static final String PARAGRAPH = "\n\n";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path configDir;

    ClaudeChatTranscripts(Path configDir) {
        this.configDir = configDir;
    }

    /** True once a turn of the session has started; Claude Code then refuses its id as new. */
    boolean exists(UUID sessionId) {
        return find(sessionId).isPresent();
    }

    List<ChatMessage> read(UUID sessionId) {
        Optional<Path> file = find(sessionId);
        if (file.isEmpty()) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(file.get(), StandardCharsets.UTF_8)) {
            Builder messages = new Builder();
            lines.map(ClaudeChatTranscripts::parse).forEach(messages::add);
            return messages.build();
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("Could not read chat transcript " + sessionId, e);
        }
    }

    /** Deletes the session file and the folder of tool output Claude Code may keep beside it. */
    void delete(UUID sessionId) {
        find(sessionId).ifPresent(file -> {
            CliAgentProcessFactory.deleteWorkDir(sibling(file, sessionId));
            CliAgentProcessFactory.deleteWorkDir(file);
        });
    }

    private Optional<Path> find(UUID sessionId) {
        Path projects = configDir.resolve(PROJECTS);
        if (!Files.isDirectory(projects)) {
            return Optional.empty();
        }
        try (Stream<Path> dirs = Files.list(projects)) {
            return dirs.map(dir -> dir.resolve(sessionId + EXTENSION))
                    .filter(Files::isRegularFile)
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path sibling(Path file, UUID sessionId) {
        return file.resolveSibling(sessionId.toString());
    }

    private static JsonNode parse(String line) {
        try {
            return JSON.readTree(line);
        } catch (JsonProcessingException e) {
            return JSON.missingNode();
        }
    }

    /** Folds the session's lines into messages, one per user prompt and one per answer. */
    private static final class Builder {

        private final List<ChatMessage> messages = new ArrayList<>();
        private final List<String> answer = new ArrayList<>();
        private Instant answerAt;

        void add(JsonNode line) {
            Optional<Role> role = roleOf(line);
            String text = text(line.path("message").path("content"));
            if (role.isEmpty() || text.isEmpty()) {
                return;
            }
            Instant at = Instant.parse(line.path("timestamp").asText());
            if (role.get() == Role.USER) {
                flushAnswer();
                messages.add(new ChatMessage(Role.USER, text, at));
            } else {
                addToAnswer(text, at);
            }
        }

        /** Whose message the line is; empty for bookkeeping and for subagent conversations. */
        private static Optional<Role> roleOf(JsonNode line) {
            if (line.path("isSidechain").asBoolean() || line.path("isMeta").asBoolean()) {
                return Optional.empty();
            }
            return switch (line.path("type").asText()) {
                case "user" -> Optional.of(Role.USER);
                case "assistant" -> Optional.of(Role.ASSISTANT);
                default -> Optional.empty();
            };
        }

        private void addToAnswer(String text, Instant at) {
            answer.add(text);
            if (answerAt == null) {
                answerAt = at;
            }
        }

        List<ChatMessage> build() {
            flushAnswer();
            return List.copyOf(messages);
        }

        private void flushAnswer() {
            if (!answer.isEmpty()) {
                messages.add(new ChatMessage(Role.ASSISTANT, String.join(PARAGRAPH, answer),
                        answerAt));
                answer.clear();
                answerAt = null;
            }
        }

        /** A plain-string prompt, or the text blocks of a block list; other blocks are not text. */
        private static String text(JsonNode content) {
            if (content.isTextual()) {
                return content.asText().strip();
            }
            List<String> parts = new ArrayList<>();
            content.forEach(block -> {
                if ("text".equals(block.path("type").asText())) {
                    parts.add(block.path("text").asText());
                }
            });
            return String.join(PARAGRAPH, parts).strip();
        }
    }
}
