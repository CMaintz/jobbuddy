package com.autoapplicant.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.chat.ProposalRefusedException;
import com.autoapplicant.domain.chat.ProposedChange;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The proposing tools turn the model's arguments into typed changes for her chat and her turn. */
class McpProposalToolsTest {

    private final McpFixture f = new McpFixture();
    private final UUID her = UUID.randomUUID();
    private final UUID posting = UUID.randomUUID();
    private final UUID application = UUID.randomUUID();
    private final ChatPlan stored = new ChatPlan(UUID.randomUUID(), her, f.chatId, McpFixture.NOW,
            List.of());

    @Test
    void a_proposal_is_made_in_the_grants_chat_as_its_user_and_shown_to_her_turn() throws Exception {
        when(f.proposals.propose(eq(her), eq(f.chatId), anyList())).thenReturn(stored);

        JsonNode result = f.content(f.call(f.grantFor(her), "propose_ignore_posting",
                "{\"posting_id\":\"" + posting + "\"}"));

        verify(f.proposals).propose(her, f.chatId,
                List.of(ProposedChange.posting(PlanItem.Kind.IGNORE_POSTING, posting)));
        assertThat(f.proposed).containsExactly(stored);
        assertThat(result.get("note").asText()).contains("Intet er ændret");
    }

    @Test
    void a_status_and_an_interview_time_arrive_typed() throws Exception {
        when(f.proposals.propose(any(), any(), anyList())).thenReturn(stored);

        f.call(f.grantFor(her), "propose_status_change",
                "{\"application_id\":\"" + application + "\",\"status\":\"interview\"}");
        f.call(f.grantFor(her), "propose_interview",
                "{\"application_id\":\"" + application + "\",\"interview_at\":\"2026-12-03T10:00\"}");

        verify(f.proposals).propose(her, f.chatId,
                List.of(ProposedChange.status(application, ApplicationStatus.INTERVIEW)));
        verify(f.proposals).propose(her, f.chatId, List.of(ProposedChange.interview(application,
                LocalDateTime.of(2026, 12, 3, 10, 0))));
    }

    @Test
    void arguments_outside_the_schema_are_refused_before_anything_is_stored() throws Exception {
        JsonNode badStatus = f.call(f.grantFor(her), "propose_status_change",
                "{\"application_id\":\"" + application + "\",\"status\":\"HIRED; DROP\"}");
        JsonNode badTime = f.call(f.grantFor(her), "propose_interview",
                "{\"application_id\":\"" + application + "\",\"interview_at\":\"på torsdag\"}");
        JsonNode badId = f.call(f.grantFor(her), "propose_save_posting",
                "{\"posting_id\":\"https://evil.example\"}");

        assertThat(List.of(badStatus, badTime, badId))
                .allSatisfy(r -> assertThat(r.get("isError").asBoolean()).isTrue());
        verify(f.proposals, never()).propose(any(), any(), anyList());
        assertThat(f.proposed).isEmpty();
    }

    @Test
    void one_answer_cannot_flood_her_with_proposals() throws Exception {
        when(f.proposals.propose(any(), any(), anyList())).thenReturn(stored);
        McpGrant turn = f.grantFor(her);
        String args = "{\"posting_id\":\"" + posting + "\"}";

        for (int i = 0; i < McpGrant.MAX_PROPOSALS_PER_TURN; i++) {
            f.call(turn, "propose_save_posting", args);
        }
        JsonNode oneTooMany = f.call(turn, "propose_save_posting", args);

        assertThat(oneTooMany.get("isError").asBoolean()).isTrue();
        assertThat(f.proposed).hasSize(McpGrant.MAX_PROPOSALS_PER_TURN);
    }

    @Test
    void a_refused_proposal_tells_the_model_why_and_shows_her_nothing() throws Exception {
        when(f.proposals.propose(any(), any(), anyList()))
                .thenThrow(new ProposalRefusedException("No such application"));

        JsonNode result = f.call(f.grantFor(her), "propose_status_change",
                "{\"application_id\":\"" + application + "\",\"status\":\"REJECTED\"}");

        assertThat(result.get("isError").asBoolean()).isTrue();
        assertThat(result.get("content").get(0).get("text").asText()).isEqualTo("No such application");
        assertThat(f.proposed).isEmpty();
    }
}
