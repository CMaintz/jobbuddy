package com.autoapplicant.adapter.mcp;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.chatplan.ChatPlanApproval;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.GlobalExceptionHandler;
import com.autoapplicant.adapter.web.controller.ChatController;
import com.autoapplicant.adapter.web.controller.ChatPlanController;
import com.autoapplicant.config.ClockConfig;
import com.autoapplicant.config.SecurityConfig;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.application.GetApplicationsUseCase;
import com.autoapplicant.port.in.auth.ProvisionLocalUserUseCase;
import com.autoapplicant.port.in.chat.ChatTurnUseCase;
import com.autoapplicant.port.in.chat.DecideChatPlanUseCase;
import com.autoapplicant.port.in.chat.ManageChatsUseCase;
import com.autoapplicant.port.in.chat.ProposeChatPlanUseCase;
import com.autoapplicant.port.in.chat.ResolveChatActionUseCase;
import com.autoapplicant.port.in.job.GetIgnoredJobsUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.in.job.GetSavedJobsUseCase;
import com.autoapplicant.port.in.job.SearchJobsUseCase;
import com.autoapplicant.port.in.job.SemanticSearchJobsUseCase;
import com.autoapplicant.port.in.user.GetAiCareerProfileUseCase;
import com.autoapplicant.port.out.chat.ChatToolsPort.Offers;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The MCP endpoint through the desktop profile's real filter chains. The turn's own token opens
 * it and nothing else; that token opens nothing but it.
 */
@WebMvcTest(
        controllers = {McpController.class, ChatController.class, ChatPlanController.class},
        properties = {
            "app.desktop.token=" + McpSecurityTest.DESKTOP_TOKEN,
            "app.desktop.shell-origin=app://jobbuddy",
            "app.data-dir=build/desktop-test-data"
        })
@ActiveProfiles("desktop")
@Import({SecurityConfig.class, SecurityContextHelper.class, GlobalExceptionHandler.class,
        McpGrants.class, McpServer.class, ClockConfig.class, SearchPostingsTool.class,
        GetPostingTool.class, GetProfileTool.class, ListApplicationsTool.class,
        UpcomingDatesTool.class, ListSavedPostingsTool.class, OpenInAppTool.class,
        StepThroughPostingsTool.class, ProposePostingTools.Save.class,
        ProposePostingTools.Ignore.class, ProposeStatusChangeTool.class, ProposeInterviewTool.class,
        ProposeDocumentsTool.class})
class McpSecurityTest {

    /** Plain words, so the secret scanner does not read it as a leaked key. */
    static final String DESKTOP_TOKEN = "mcp-test-desktop-token-for-the-filter";
    private static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":"
            + "\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
            + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";

    @Autowired MockMvc mvc;
    @Autowired McpGrants grants;

    @MockitoBean ProvisionLocalUserUseCase localUser;
    @MockitoBean ManageChatsUseCase chats;
    @MockitoBean ChatTurnUseCase turns;
    @MockitoBean SearchJobsUseCase search;
    @MockitoBean SemanticSearchJobsUseCase semantic;
    @MockitoBean GetRecommendationsUseCase recommendations;
    @MockitoBean GetJobByIdUseCase jobs;
    @MockitoBean GetApplicationsUseCase applications;
    @MockitoBean GetSavedJobsUseCase saved;
    @MockitoBean GetIgnoredJobsUseCase ignored;
    @MockitoBean GetAiCareerProfileUseCase profile;
    @MockitoBean ResolveChatActionUseCase resolver;
    @MockitoBean ProposeChatPlanUseCase proposals;
    @MockitoBean DecideChatPlanUseCase decisions;
    @MockitoBean ChatPlanApproval approval;

    private static final Offers NO_OFFERS = new Offers(action -> { }, plan -> { });

    private final UUID her = UUID.randomUUID();

    @BeforeEach
    void givenTheLocalUser() {
        when(localUser.provisionLocalUser()).thenReturn(new User(her, "local@jobbuddy.invalid",
                null, null, null, UserRole.USER, true, true, Instant.now(), Instant.now()));
    }

    @Test
    void the_turns_token_opens_the_mcp_endpoint() throws Exception {
        String token = grants.issue(her, UUID.randomUUID(), NO_OFFERS);

        mvc.perform(initialize().header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serverInfo.name").value("jobbuddy"));
    }

    @Test
    void without_a_live_turn_token_the_mcp_endpoint_is_closed() throws Exception {
        String revoked = grants.issue(her, UUID.randomUUID(), NO_OFFERS);
        grants.revoke(revoked);

        mvc.perform(initialize()).andExpect(status().isForbidden());
        mvc.perform(initialize().header("Authorization", "Bearer " + revoked))
                .andExpect(status().isForbidden());
        mvc.perform(initialize().header("Authorization", "Bearer " + DESKTOP_TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(initialize().header("X-JobBuddy-Token", DESKTOP_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void the_turns_token_from_another_machine_is_refused() throws Exception {
        String token = grants.issue(her, UUID.randomUUID(), NO_OFFERS);

        mvc.perform(initialize().header("Authorization", "Bearer " + token).with(request -> {
            request.setRemoteAddr("192.168.1.20");
            return request;
        })).andExpect(status().isForbidden());
    }

    @Test
    void the_turns_token_opens_nothing_but_the_mcp_endpoint() throws Exception {
        String token = grants.issue(her, UUID.randomUUID(), NO_OFFERS);

        mvc.perform(get("/api/v1/chats").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/chats").header("X-JobBuddy-Token", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void the_turns_token_cannot_approve_or_reject_what_the_chat_proposed() throws Exception {
        String token = grants.issue(her, UUID.randomUUID(), NO_OFFERS);
        String item = "/api/v1/chats/plans/" + UUID.randomUUID() + "/items/" + UUID.randomUUID();

        for (String decision : List.of("/approve", "/reject")) {
            mvc.perform(post(item + decision).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(post(item + decision).header("X-JobBuddy-Token", token))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(approval, decisions);
    }

    private static MockHttpServletRequestBuilder initialize() {
        return post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(INITIALIZE);
    }
}
