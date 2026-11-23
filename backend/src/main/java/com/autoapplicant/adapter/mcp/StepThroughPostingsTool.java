package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.port.in.chat.ResolveChatActionUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("desktop")
public class StepThroughPostingsTool extends ActionTool {

    public StepThroughPostingsTool(ResolveChatActionUseCase resolver) {
        super(resolver);
    }

    @Override
    public String name() {
        return ChatActionCalls.STEP_THROUGH;
    }

    @Override
    public String description() {
        return "Show the user a button that walks her through these postings one at a time, in"
                + " this order, with next and previous. Use it when she wants to go through several."
                + " At most " + ChatAction.MAX_STEP_THROUGH + ".";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of(ChatActionCalls.POSTING_IDS,
                McpSchema.ids("The postings, in the order to show them", ChatAction.MAX_STEP_THROUGH)),
                List.of(ChatActionCalls.POSTING_IDS));
    }

    @Override
    ChatAction requested(JsonNode arguments) {
        return ChatActionCalls.stepThrough(arguments);
    }
}
