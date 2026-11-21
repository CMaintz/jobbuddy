package com.autoapplicant.adapter.mcp;

import static org.mockito.Mockito.mock;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.application.GetApplicationsUseCase;
import com.autoapplicant.port.in.chat.ResolveChatActionUseCase;
import com.autoapplicant.port.in.job.GetIgnoredJobsUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.in.job.GetSavedJobsUseCase;
import com.autoapplicant.port.in.job.SearchJobsUseCase;
import com.autoapplicant.port.in.job.SemanticSearchJobsUseCase;
import com.autoapplicant.port.in.user.GetAiCareerProfileUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The MCP server with every tool, over mocked read use cases. */
final class McpFixture {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    final SearchJobsUseCase search = mock(SearchJobsUseCase.class);
    final SemanticSearchJobsUseCase semantic = mock(SemanticSearchJobsUseCase.class);
    final GetRecommendationsUseCase recommendations = mock(GetRecommendationsUseCase.class);
    final GetJobByIdUseCase jobs = mock(GetJobByIdUseCase.class);
    final GetApplicationsUseCase applications = mock(GetApplicationsUseCase.class);
    final GetSavedJobsUseCase saved = mock(GetSavedJobsUseCase.class);
    final GetIgnoredJobsUseCase ignored = mock(GetIgnoredJobsUseCase.class);
    final GetAiCareerProfileUseCase profile = mock(GetAiCareerProfileUseCase.class);
    final ResolveChatActionUseCase resolver = mock(ResolveChatActionUseCase.class);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final ObjectMapper json = new ObjectMapper();
    final McpServer server = new McpServer(McpTools.all(this), json);

    /** Buttons each user's turn was offered. */
    final List<ChatAction> offered = new ArrayList<>();

    McpGrant grantFor(UUID userId) {
        return new McpGrant(userId, offered::add);
    }

    /** The tool's result, parsed; fails the test on a protocol error. */
    JsonNode call(McpGrant grant, String tool, String argumentsJson) throws Exception {
        JsonNode result = rpc(grant, "tools/call",
                "{\"name\":\"" + tool + "\",\"arguments\":" + argumentsJson + "}").get("result");
        if (result == null) {
            throw new AssertionError("no result for " + tool);
        }
        return result;
    }

    /** The JSON text the tool answered with, parsed. */
    JsonNode content(JsonNode result) throws Exception {
        return json.readTree(result.get("content").get(0).get("text").asText());
    }

    JsonNode rpc(McpGrant grant, String method, String paramsJson) throws Exception {
        ObjectNode request = (ObjectNode) json.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"" + method + "\",\"params\":"
                        + paramsJson + "}");
        return server.handle(request, grant).orElseThrow();
    }

    static Job job(UUID id, String title, String company) {
        return Job.builder().id(id).title(title).companyName(company).location("Aarhus")
                .postedAt(NOW.minusSeconds(86_400)).build();
    }

    static Application application(UUID userId, UUID jobId, ApplicationStatus status,
                                   LocalDate deadline, LocalDateTime interviewAt) {
        return new Application(UUID.randomUUID(), userId, jobId, status, null, null, null, null,
                null, null, null, null, null, null, null, NOW, NOW, null, null, deadline,
                interviewAt);
    }
}
