package com.autoapplicant.adapter.mcp;

import java.util.List;

/** Every production tool, built on the fixture's mocks the way Spring wires them. */
final class McpTools {

    private McpTools() {}

    static List<McpTool> all(McpFixture f) {
        return List.of(
                new SearchPostingsTool(f.search, f.semantic, f.recommendations, f.ignored, f.clock),
                new GetPostingTool(f.jobs),
                new GetProfileTool(f.profile),
                new ListApplicationsTool(f.applications, f.jobs),
                new UpcomingDatesTool(f.applications, f.jobs, f.clock),
                new ListSavedPostingsTool(f.saved, f.ignored, f.jobs),
                new OpenInAppTool(f.resolver),
                new StepThroughPostingsTool(f.resolver),
                new ProposePostingTools.Save(f.proposals),
                new ProposePostingTools.Ignore(f.proposals),
                new ProposeStatusChangeTool(f.proposals),
                new ProposeInterviewTool(f.proposals));
    }
}
