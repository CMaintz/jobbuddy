package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.port.in.chat.ResolveChatActionUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * A tool that offers her a button. The model never navigates or acts: the server checks what it
 * proposed, hands the checked button to her turn, and she decides whether to click it.
 */
abstract class ActionTool implements McpTool {

    static final String OFFERED = "Brugeren ser nu en knap til det. Intet sker, før hun selv"
            + " klikker. Nævn kort i dit svar, hvad knappen gør.";

    private final ResolveChatActionUseCase resolver;

    ActionTool(ResolveChatActionUseCase resolver) {
        this.resolver = resolver;
    }

    /** The button the call asks for, unchecked. */
    abstract ChatAction requested(JsonNode arguments);

    @Override
    public final Object call(McpGrant grant, JsonNode arguments) {
        ChatAction asked = requested(arguments);
        ChatAction checked = resolver.resolve(asked.kind(), asked.postingIds())
                .orElseThrow(() -> new McpToolException("No such posting, or not that many;"
                        + " use ids returned by the other tools"));
        grant.offers().actions().accept(checked);
        return Map.of("shown", true, "note", OFFERED);
    }
}
