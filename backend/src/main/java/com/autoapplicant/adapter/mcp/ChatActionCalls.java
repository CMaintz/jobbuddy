package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.chat.ChatAction.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the button a call to {@code open_in_app} or {@code step_through_postings} asks for. What
 * comes out is unchecked: ids only, for {@code ResolveChatActionUseCase} to check.
 */
final class ChatActionCalls {

    static final String OPEN_IN_APP = "open_in_app";
    static final String STEP_THROUGH = "step_through_postings";

    static final String TARGET = "target";
    static final String POSTING_ID = "posting_id";
    static final String POSTING_IDS = "posting_ids";
    static final int MAX_STEPS = 10;

    /** What the model may ask the app to open; anything else, a URL included, is refused. */
    static final Map<String, Kind> TARGETS = Map.of(
            "posting", Kind.OPEN_POSTING,
            "start_application", Kind.START_APPLICATION,
            "pipeline", Kind.SHOW_PIPELINE);

    private ChatActionCalls() {}

    static ChatAction openInApp(JsonNode input) {
        Kind kind = McpArgs.choice(input, TARGET, List.copyOf(TARGETS.keySet()))
                .map(TARGETS::get)
                .orElseThrow(() -> new McpToolException(TARGET + " is required"));
        List<UUID> ids = kind == Kind.SHOW_PIPELINE ? List.of() : List.of(McpArgs.id(input, POSTING_ID));
        return unchecked(kind, ids);
    }

    static ChatAction stepThrough(JsonNode input) {
        return unchecked(Kind.STEP_THROUGH, McpArgs.ids(input, POSTING_IDS, MAX_STEPS));
    }

    private static ChatAction unchecked(Kind kind, List<UUID> ids) {
        return new ChatAction(kind, ids.stream().map(ChatAction.Posting::unresolved).toList());
    }
}
