package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.chat.ProposedChange;
import com.autoapplicant.port.in.chat.ProposeChatPlanUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Proposals about one posting: saving it, or hiding it from her lists. */
final class ProposePostingTools {

    static final String POSTING_ID = "posting_id";

    private ProposePostingTools() {}

    static Map<String, Object> schema() {
        return McpSchema.object(Map.of(POSTING_ID, McpSchema.string("The posting")),
                List.of(POSTING_ID));
    }

    static List<ProposedChange> requested(PlanItem.Kind kind, JsonNode arguments) {
        return List.of(ProposedChange.posting(kind, McpArgs.id(arguments, POSTING_ID)));
    }

    @Component
    @Profile("desktop")
    public static class Save extends ProposalTool {

        public Save(ProposeChatPlanUseCase proposals) {
            super(proposals);
        }

        @Override
        public String name() {
            return "propose_save_posting";
        }

        @Override
        public String description() {
            return "Propose saving a posting to her saved postings. She sees the proposal with"
                    + " Approve and Reject buttons; nothing is saved unless she approves it.";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return schema();
        }

        @Override
        List<ProposedChange> requested(JsonNode arguments) {
            return ProposePostingTools.requested(PlanItem.Kind.SAVE_POSTING, arguments);
        }
    }

    @Component
    @Profile("desktop")
    public static class Ignore extends ProposalTool {

        public Ignore(ProposeChatPlanUseCase proposals) {
            super(proposals);
        }

        @Override
        public String name() {
            return "propose_ignore_posting";
        }

        @Override
        public String description() {
            return "Propose hiding a posting she is not interested in, so it leaves her lists and"
                    + " her saved postings. She sees the proposal with Approve and Reject buttons;"
                    + " nothing changes unless she approves it.";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return schema();
        }

        @Override
        List<ProposedChange> requested(JsonNode arguments) {
            return ProposePostingTools.requested(PlanItem.Kind.IGNORE_POSTING, arguments);
        }
    }
}
