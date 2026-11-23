package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.chat.ProposedChange;
import com.autoapplicant.port.in.chat.ProposeChatPlanUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Proposes a tailored CV and cover letter for each of several postings, as one plan she can
 * approve whole or posting by posting. Writing them is long and uses her subscription, so it
 * runs only on her approval, like every other change.
 */
@Component
@Profile("desktop")
public class ProposeDocumentsTool extends ProposalTool {

    static final String POSTING_IDS = "posting_ids";

    /** Each posting is a few minutes of generation; more than this is a day's work queued at once. */
    static final int MAX_POSTINGS = 10;

    public ProposeDocumentsTool(ProposeChatPlanUseCase proposals) {
        super(proposals);
    }

    @Override
    public String name() {
        return "propose_tailored_documents";
    }

    @Override
    public String description() {
        return "Propose writing a tailored CV and cover letter for each of these postings, with her"
                + " profile and the app's own fact checks. She sees one proposal listing the"
                + " postings and approves all of them or one at a time; nothing is written unless"
                + " she approves. The documents are saved for her to review in the app, never"
                + " sent. At most " + MAX_POSTINGS + " postings.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of(POSTING_IDS,
                McpSchema.ids("The postings, from the other tools", MAX_POSTINGS)),
                List.of(POSTING_IDS));
    }

    /** A posting named twice is written once. */
    @Override
    List<ProposedChange> requested(JsonNode arguments) {
        return new LinkedHashSet<>(McpArgs.ids(arguments, POSTING_IDS, MAX_POSTINGS)).stream()
                .map(id -> ProposedChange.posting(PlanItem.Kind.GENERATE_DOCUMENTS, id))
                .toList();
    }
}
