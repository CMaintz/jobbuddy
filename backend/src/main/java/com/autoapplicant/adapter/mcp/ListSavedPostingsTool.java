package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.port.in.job.GetIgnoredJobsUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import com.autoapplicant.port.in.job.GetSavedJobsUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** The postings she saved, or the ones she chose to ignore. */
@Component
@Profile("desktop")
public class ListSavedPostingsTool implements McpTool {

    static final int MAX_RESULTS = 25;
    private static final String SAVED = "saved";
    private static final String IGNORED = "ignored";

    private final GetSavedJobsUseCase saved;
    private final GetIgnoredJobsUseCase ignored;
    private final GetJobByIdUseCase jobs;

    public ListSavedPostingsTool(GetSavedJobsUseCase saved, GetIgnoredJobsUseCase ignored,
                                 GetJobByIdUseCase jobs) {
        this.saved = saved;
        this.ignored = ignored;
        this.jobs = jobs;
    }

    @Override
    public String name() {
        return "list_saved_postings";
    }

    @Override
    public String description() {
        return "Postings the user saved (default), or the ones she ignored.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of("which", McpSchema.oneOf("Saved or ignored postings",
                List.of(SAVED, IGNORED))), List.of());
    }

    @Override
    public Object call(McpGrant grant, JsonNode args) {
        boolean wantIgnored = McpArgs.choice(args, "which", List.of(SAVED, IGNORED))
                .map(IGNORED::equals).orElse(false);
        List<PostingSummary> postings = wantIgnored
                ? ignored.getIgnoredJobs(grant.userId()).stream()
                        .limit(MAX_RESULTS)
                        .map(IgnoredJob::jobId)
                        .flatMap(id -> jobs.getJobById(id).stream())
                        .map(PostingSummary::of)
                        .toList()
                : saved.getSavedJobs(grant.userId()).stream()
                        .limit(MAX_RESULTS)
                        .map(PostingSummary::of)
                        .toList();
        return Map.of("postings", postings);
    }
}
