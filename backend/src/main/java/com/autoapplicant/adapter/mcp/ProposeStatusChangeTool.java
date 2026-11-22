package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.chat.ProposedChange;
import com.autoapplicant.port.in.chat.ProposeChatPlanUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("desktop")
public class ProposeStatusChangeTool extends ProposalTool {

    static final String STATUS = "status";

    public ProposeStatusChangeTool(ProposeChatPlanUseCase proposals) {
        super(proposals);
    }

    @Override
    public String name() {
        return "propose_status_change";
    }

    @Override
    public String description() {
        return "Propose moving one of her applications to a new status, for example when she"
                + " tells you she got an interview or a rejection. Only the moves the app allows"
                + " from the current status are accepted. She sees the proposal with Approve and"
                + " Reject buttons; nothing changes unless she approves it.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of(
                APPLICATION_ID, McpSchema.string(APPLICATION_DESCRIPTION),
                STATUS, McpSchema.oneOf("The new status", statuses())),
                List.of(APPLICATION_ID, STATUS));
    }

    @Override
    List<ProposedChange> requested(JsonNode arguments) {
        ApplicationStatus status = McpArgs.choice(arguments, STATUS, statuses())
                .map(ApplicationStatus::valueOf)
                .orElseThrow(() -> new McpToolException(STATUS + " is required"));
        return List.of(ProposedChange.status(McpArgs.id(arguments, APPLICATION_ID), status));
    }

    private static List<String> statuses() {
        return Arrays.stream(ApplicationStatus.values()).map(Enum::name).toList();
    }
}
