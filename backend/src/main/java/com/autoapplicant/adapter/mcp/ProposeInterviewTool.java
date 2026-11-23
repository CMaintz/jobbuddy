package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ProposedChange;
import com.autoapplicant.port.in.chat.ProposeChatPlanUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("desktop")
public class ProposeInterviewTool extends ProposalTool {

    static final String INTERVIEW_AT = "interview_at";

    public ProposeInterviewTool(ProposeChatPlanUseCase proposals) {
        super(proposals);
    }

    @Override
    public String name() {
        return "propose_interview";
    }

    @Override
    public String description() {
        return "Propose noting the date and time of a job interview on one of her applications,"
                + " so the app reminds her. Her deadline on it is kept. She sees the proposal with"
                + " Approve and Reject buttons; nothing changes unless she approves it.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of(
                APPLICATION_ID, McpSchema.string(APPLICATION_DESCRIPTION),
                INTERVIEW_AT, McpSchema.string("Local date and time as she gave it, like"
                        + " 2026-12-03T10:00")),
                List.of(APPLICATION_ID, INTERVIEW_AT));
    }

    @Override
    List<ProposedChange> requested(JsonNode arguments) {
        return List.of(ProposedChange.interview(McpArgs.id(arguments, APPLICATION_ID),
                McpArgs.dateTime(arguments, INTERVIEW_AT)));
    }
}
