package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.port.in.user.GetAiCareerProfileUseCase;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Her profile and the outline of her master CV, from the identity-free projection generation
 * uses: no name, contact details or links. Roles keep their titles and dates but not their
 * descriptions, which is enough to judge fit at a fraction of the tokens.
 */
@Component
@Profile("desktop")
public class GetProfileTool implements McpTool {

    private static final int SUMMARY_CHARS = 600;
    private static final int MAX_SKILLS = 30;
    private static final int MAX_ROLES = 8;
    private static final int MAX_EDUCATION = 5;

    private final GetAiCareerProfileUseCase profiles;

    public GetProfileTool(GetAiCareerProfileUseCase profiles) {
        this.profiles = profiles;
    }

    record Entry(String title, String organisation, String period) {
        static Entry of(StructuredDocumentItem item) {
            return new Entry(item.title(), item.subtitle(), item.dateRange());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProfileSummary(String headline, String summary, String northStar,
                          List<String> targetRoles, List<String> skills, List<String> languages,
                          List<Entry> experience, List<Entry> education, String availability) {}

    @Override
    public String name() {
        return "get_profile";
    }

    @Override
    public String description() {
        return "The user's career profile and the outline of her CV: headline, summary, target"
                + " roles, skills, languages, roles held and education.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.none();
    }

    @Override
    public Object call(McpGrant grant, JsonNode args) {
        CareerProfileForAi p = profiles.getAiCareerProfile(grant.userId());
        return new ProfileSummary(p.headline(), McpText.cut(p.profile(), SUMMARY_CHARS),
                p.northStar(), p.targetArchetypes(), first(p.skills(), MAX_SKILLS),
                p.spokenLanguages(), entries(p.experience(), MAX_ROLES),
                entries(p.education(), MAX_EDUCATION), p.availability());
    }

    private static List<Entry> entries(List<StructuredDocumentItem> items, int max) {
        return items == null ? List.of() : items.stream().limit(max).map(Entry::of).toList();
    }

    private static List<String> first(List<String> values, int max) {
        return values == null ? List.of() : values.stream().limit(max).toList();
    }
}
