package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;

/**
 * Reads one turn of {@code claude -p --output-format stream-json --include-partial-messages}, a
 * JSON object per line. The answer's text arrives as {@code text_delta}s; the turn ends with a
 * {@code result} line. Failures are classified from the structured fields Claude Code sets
 * ({@code error} on an assistant message, a rejected {@code rate_limit_event}), never from the
 * wording of a message, which changes between releases. One parser per turn: it remembers why a
 * turn is failing until the result line confirms it.
 */
final class ClaudeStreamParser {

    /** The {@code error} values of an assistant message that have a failure of their own. */
    private static final Map<String, ChatFailure> ERRORS = Map.of(
            "authentication_failed", ChatFailure.NOT_LOGGED_IN,
            "rate_limit", ChatFailure.USAGE_LIMIT,
            "billing_error", ChatFailure.USAGE_LIMIT);

    private static final String REJECTED = "rejected";

    private static final ObjectMapper JSON = new ObjectMapper();

    private ChatFailure failure;

    /** The event this line means for the chat, if any. Lines that are not JSON are skipped. */
    Optional<ChatEvent> parse(String line) {
        JsonNode node = read(line);
        String type = node.path("type").asText();
        if ("stream_event".equals(type)) {
            return textDelta(node);
        }
        if ("result".equals(type)) {
            return Optional.of(node.path("is_error").asBoolean() ? atExit() : completed());
        }
        noteFailure(type, node);
        return Optional.empty();
    }

    /** How a turn ends whose process exited without a successful result line. */
    ChatEvent atExit() {
        return new ChatEvent.Failed(failure != null ? failure : ChatFailure.FAILED);
    }

    private static Optional<ChatEvent> textDelta(JsonNode node) {
        // Text from a subagent's own conversation carries the tool call it runs under.
        if (!node.path("parent_tool_use_id").isNull()) {
            return Optional.empty();
        }
        JsonNode event = node.path("event");
        JsonNode delta = event.path("delta");
        if (!"content_block_delta".equals(event.path("type").asText())
                || !"text_delta".equals(delta.path("type").asText())) {
            return Optional.empty();
        }
        return Optional.of(new ChatEvent.Delta(delta.path("text").asText()));
    }

    /** Remembers why the turn is failing, for the result line or the exit that follows. */
    private void noteFailure(String type, JsonNode node) {
        if ("assistant".equals(type) && node.hasNonNull("error")) {
            failure = ERRORS.getOrDefault(node.get("error").asText(), ChatFailure.FAILED);
        } else if ("rate_limit_event".equals(type)
                && REJECTED.equals(node.path("rate_limit_info").path("status").asText())) {
            failure = ChatFailure.USAGE_LIMIT;
        }
    }

    private static ChatEvent completed() {
        return new ChatEvent.Completed();
    }

    private static JsonNode read(String line) {
        try {
            return JSON.readTree(line);
        } catch (JsonProcessingException e) {
            return JSON.missingNode();
        }
    }
}
