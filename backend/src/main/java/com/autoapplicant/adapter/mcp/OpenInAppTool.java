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
public class OpenInAppTool extends ActionTool {

    public OpenInAppTool(ResolveChatActionUseCase resolver) {
        super(resolver);
    }

    @Override
    public String name() {
        return ChatActionCalls.OPEN_IN_APP;
    }

    @Override
    public String description() {
        return "Show the user a button that opens a screen in JobBuddy: a posting, a new"
                + " application for a posting, or her pipeline. The app does nothing until she"
                + " clicks it.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of(
                ChatActionCalls.TARGET, McpSchema.oneOf("What to open",
                        List.copyOf(ChatActionCalls.TARGETS.keySet())),
                ChatActionCalls.POSTING_ID, McpSchema.string(
                        "The posting, for 'posting' and 'start_application'")),
                List.of(ChatActionCalls.TARGET));
    }

    @Override
    ChatAction requested(JsonNode arguments) {
        return ChatActionCalls.openInApp(arguments);
    }
}
