package com.autoapplicant.adapter.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * The endpoints whose handlers make a paid AI provider call, and so share the per-user AI
 * request budget enforced by {@link AiRateLimitFilter}.
 *
 * <p>Each entry is a method plus an Ant-style path pattern, because some AI calls sit on a GET
 * (tailored evidence questions, semantic search) and some POSTs next to them are plain CRUD.
 * When a new endpoint reaches a {@code ChatProviderPort} or embeds per request, add it here.
 *
 * <p>Deliberately not listed: endpoints that only read cached embeddings (recommendations,
 * dashboard, analytics, similar jobs), the profile update whose embedding refresh is an async
 * side effect, and outreach / company research, which are tracking CRUD with no model call.
 */
final class AiRateLimitedEndpoints {

    /** Everything under the AI controller: generation, tailored CV, parse, refine, review. */
    static final String AI_CONTROLLER = "/api/v1/ai/**";

    /** CV PDF import, parsed into a profile by the model. */
    static final String CV_PDF_IMPORT = "/api/v1/profile/import/cv-pdf";

    /** LinkedIn profile PDF import, parsed by the model. */
    static final String LINKEDIN_PDF_IMPORT = "/api/v1/users/me/import/linkedin-pdf";

    /** Writing-style analysis of the user's samples. */
    static final String WRITING_STYLE_ANALYZE = "/api/v1/users/me/writing-style/analyze";

    /** Interview question generation for a job. */
    static final String INTERVIEW_QUESTIONS = "/api/v1/jobs/*/interview-prep/generate";

    /** Full interview prep pack for a job. */
    static final String INTERVIEW_PACK = "/api/v1/jobs/*/interview-prep/pack";

    /** Mock-interview roleplay turn. */
    static final String INTERVIEW_ROLEPLAY = "/api/v1/jobs/*/interview-prep/roleplay";

    /** Evidence gaps with model-tailored questions (a GET that calls the model). */
    static final String EVIDENCE_TAILORED = "/api/v1/skills/evidence-gaps/tailored";

    /** Restructuring a free-text evidence answer into situation / action / result. */
    static final String EVIDENCE_DRAFT = "/api/v1/skills/evidence/draft";

    /** Semantic job search, which embeds every query. */
    static final String SEMANTIC_SEARCH = "/api/v1/jobs/search/semantic";

    /** Admin LinkedIn keyword plan generation and refresh. */
    static final String LINKEDIN_PLAN = "/api/v1/admin/linkedin/plan/**";

    /** Admin LinkedIn crawl, which refreshes keyword plans first. */
    static final String LINKEDIN_RUN = "/api/v1/admin/linkedin/run";

    private static final List<Route> ROUTES = List.of(
            new Route(HttpMethod.POST, AI_CONTROLLER),
            new Route(HttpMethod.POST, CV_PDF_IMPORT),
            new Route(HttpMethod.POST, LINKEDIN_PDF_IMPORT),
            new Route(HttpMethod.POST, WRITING_STYLE_ANALYZE),
            new Route(HttpMethod.POST, INTERVIEW_QUESTIONS),
            new Route(HttpMethod.POST, INTERVIEW_PACK),
            new Route(HttpMethod.POST, INTERVIEW_ROLEPLAY),
            new Route(HttpMethod.GET, EVIDENCE_TAILORED),
            new Route(HttpMethod.POST, EVIDENCE_DRAFT),
            new Route(HttpMethod.GET, SEMANTIC_SEARCH),
            new Route(HttpMethod.POST, LINKEDIN_PLAN),
            new Route(HttpMethod.POST, LINKEDIN_RUN));

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private AiRateLimitedEndpoints() {}

    /** Whether a request with this method and path makes a paid AI call. */
    static boolean matches(String method, String path) {
        return ROUTES.stream().anyMatch(route -> route.matches(method, path));
    }

    private record Route(HttpMethod method, String pattern) {

        boolean matches(String requestMethod, String path) {
            return method.matches(requestMethod) && MATCHER.match(pattern, path);
        }
    }
}
