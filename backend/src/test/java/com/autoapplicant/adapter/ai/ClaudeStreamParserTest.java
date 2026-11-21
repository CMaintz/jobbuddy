package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Lines shaped like the ones Claude Code 2.1 prints with --output-format stream-json. */
class ClaudeStreamParserTest {

    private final ClaudeStreamParser parser = new ClaudeStreamParser();

    @Test
    void answer_text_is_relayed_piece_by_piece_and_the_result_completes_the_turn() {
        List<ChatEvent> events = parseAll(
                ClaudeStreamLines.INIT,
                ClaudeStreamLines.textDelta("2+2 er "),
                ClaudeStreamLines.textDelta("4."),
                ClaudeStreamLines.RESULT_OK);

        assertThat(events).containsExactly(
                new ChatEvent.Delta("2+2 er "), new ChatEvent.Delta("4."), new ChatEvent.Completed());
    }

    @Test
    void thinking_and_bookkeeping_lines_are_not_shown() {
        List<ChatEvent> events = parseAll(
                ClaudeStreamLines.thinkingDelta("hmm, the user wants"),
                "{\"type\":\"system\",\"subtype\":\"thinking_tokens\",\"estimated_tokens\":50}",
                "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"2+2 er 4.\"}]}}",
                ClaudeStreamLines.RATE_LIMIT_ALLOWED,
                "not json at all");

        assertThat(events).isEmpty();
    }

    @Test
    void a_missing_login_is_reported_as_such() {
        List<ChatEvent> events = parseAll(
                ClaudeStreamLines.assistantError("authentication_failed",
                        "Not logged in · Please run /login"),
                ClaudeStreamLines.resultError("Not logged in · Please run /login"));

        assertThat(events).containsExactly(new ChatEvent.Failed(ChatFailure.NOT_LOGGED_IN));
    }

    @Test
    void a_rate_limit_error_is_a_usage_limit() {
        List<ChatEvent> events = parseAll(
                ClaudeStreamLines.assistantError("rate_limit", "Limit reached"),
                ClaudeStreamLines.resultError("Limit reached"));

        assertThat(events).containsExactly(new ChatEvent.Failed(ChatFailure.USAGE_LIMIT));
    }

    @Test
    void a_rejected_rate_limit_event_is_a_usage_limit_even_with_a_generic_error() {
        List<ChatEvent> events = parseAll(
                ClaudeStreamLines.RATE_LIMIT_REJECTED,
                ClaudeStreamLines.resultError("API Error"));

        assertThat(events).containsExactly(new ChatEvent.Failed(ChatFailure.USAGE_LIMIT));
    }

    @Test
    void any_other_error_result_is_a_plain_failure() {
        assertThat(parseAll(ClaudeStreamLines.resultError("Something broke")))
                .containsExactly(new ChatEvent.Failed(ChatFailure.FAILED));
    }

    @Test
    void text_from_a_subagent_is_not_part_of_the_answer() {
        String nested = ClaudeStreamLines.textDelta("from a subagent")
                .replace("\"parent_tool_use_id\":null", "\"parent_tool_use_id\":\"toolu_1\"");

        assertThat(parseAll(nested)).isEmpty();
    }

    @Test
    void an_exit_without_a_result_is_a_failure_that_keeps_an_earlier_reason() {
        assertThat(parser.atExit()).isEqualTo(new ChatEvent.Failed(ChatFailure.FAILED));

        ClaudeStreamParser limited = new ClaudeStreamParser();
        limited.parse(ClaudeStreamLines.RATE_LIMIT_REJECTED);
        assertThat(limited.atExit()).isEqualTo(new ChatEvent.Failed(ChatFailure.USAGE_LIMIT));
    }

    private List<ChatEvent> parseAll(String... lines) {
        return Stream.of(lines).map(parser::parse).flatMap(Optional::stream).toList();
    }
}
