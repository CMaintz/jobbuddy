package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.ProposalRefusedException;
import com.autoapplicant.domain.chat.ProposedChange;
import com.autoapplicant.port.in.chat.ProposeChatPlanUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * A tool that proposes a change to her data. Posting text reaches the model, so the model may be
 * steered into calling this: all it can do is store a proposal, checked against her data, that
 * she sees as a card and must approve herself in the app. No tool can approve.
 */
abstract class ProposalTool implements McpTool {

    static final String PROPOSED = "Brugeren ser nu forslaget med knapperne Godkend og Afvis."
            + " Intet er ændret endnu, og intet sker, før hun selv godkender. Sig ikke, at det er"
            + " gjort; bed hende se forslaget igennem.";

    static final String APPLICATION_ID = "application_id";
    static final String APPLICATION_DESCRIPTION = "The application, from list_applications";

    private final ProposeChatPlanUseCase proposals;

    ProposalTool(ProposeChatPlanUseCase proposals) {
        this.proposals = proposals;
    }

    /** The changes the call asks for, unchecked. */
    abstract List<ProposedChange> requested(JsonNode arguments);

    @Override
    public final boolean readOnly() {
        return false;
    }

    @Override
    public final Object call(McpGrant grant, JsonNode arguments) {
        List<ProposedChange> changes = requested(arguments);
        if (!grant.takeProposal()) {
            throw new McpToolException("That is as many proposals as one answer may make; let her"
                    + " decide on these first");
        }
        ChatPlan plan;
        try {
            plan = proposals.propose(grant.userId(), grant.chatId(), changes);
        } catch (ProposalRefusedException e) {
            throw new McpToolException(e.getMessage());
        }
        grant.offers().plans().accept(plan);
        return Map.of("proposed", true, "note", PROPOSED);
    }
}
