package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.search.JobSearchFilters;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.port.in.job.GetIgnoredJobsUseCase;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.in.job.SearchJobsUseCase;
import com.autoapplicant.port.in.job.SemanticSearchJobsUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Finds postings for her. Without words it is her own ranked matches. With words it is a text
 * search followed by a semantic one (by meaning, so "presse" also finds "kommunikation"), merged
 * without repeats and scored with her matches where it has one. The semantic half embeds the
 * query, one embedding API call per search, and finds nothing when no embedding key is set. What
 * she has ignored never comes back.
 */
@Component
@Profile("desktop")
public class SearchPostingsTool implements McpTool {

    static final int MAX_RESULTS = 10;
    private static final int DEFAULT_RESULTS = 5;
    private static final int POOL = 100;
    private static final int MAX_QUERY_CHARS = 200;
    private static final int MAX_DAYS = 365;

    private final SearchJobsUseCase search;
    private final SemanticSearchJobsUseCase semantic;
    private final GetRecommendationsUseCase recommendations;
    private final GetIgnoredJobsUseCase ignored;
    private final Clock clock;

    public SearchPostingsTool(SearchJobsUseCase search, SemanticSearchJobsUseCase semantic,
                              GetRecommendationsUseCase recommendations,
                              GetIgnoredJobsUseCase ignored, Clock clock) {
        this.search = search;
        this.semantic = semantic;
        this.recommendations = recommendations;
        this.ignored = ignored;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "search_postings";
    }

    @Override
    public String description() {
        return "Find job postings for the user. Without a query: her best matches, ranked by match"
                + " score (0-100). With a query: postings matching the words or their meaning,"
                + " with her match score where there is one. Returns at most " + MAX_RESULTS + ".";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of(
                "query", McpSchema.string("Words to search for; leave out for her best matches"),
                "category", McpSchema.oneOf("Job category", categories()),
                "location", McpSchema.string("Town or region the posting must mention"),
                "posted_within_days", McpSchema.integer("Only postings this new", 1, MAX_DAYS),
                "limit", McpSchema.integer("How many to return", 1, MAX_RESULTS)), List.of());
    }

    @Override
    public Object call(McpGrant grant, JsonNode args) {
        Criteria criteria = Criteria.of(args, clock);
        Map<UUID, MatchResult> matches = recommendations.getRecommendations(grant.userId(), POOL)
                .stream().collect(Collectors.toMap(MatchResult::jobId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
        Set<UUID> hidden = ignored.getIgnoredJobs(grant.userId()).stream()
                .map(IgnoredJob::jobId).collect(Collectors.toSet());
        return Map.of("postings", candidates(criteria, matches, grant.userId())
                .filter(job -> !hidden.contains(job.id()) && criteria.accepts(job))
                .limit(criteria.limit())
                .map(job -> PostingSummary.of(job, matches.get(job.id())))
                .toList());
    }

    /** What she asked for, checked. */
    private record Criteria(Optional<String> query, Optional<String> category,
                            Optional<String> location, Optional<Instant> since, int limit) {

        static Criteria of(JsonNode args, Clock clock) {
            Optional<Instant> since = args.has("posted_within_days")
                    ? Optional.of(clock.instant().minus(Duration.ofDays(
                            McpArgs.number(args, "posted_within_days", MAX_DAYS, 1, MAX_DAYS))))
                    : Optional.empty();
            return new Criteria(McpArgs.text(args, "query", MAX_QUERY_CHARS),
                    McpArgs.choice(args, "category", categories()),
                    McpArgs.text(args, "location", MAX_QUERY_CHARS)
                            .map(l -> l.toLowerCase(Locale.ROOT)),
                    since,
                    McpArgs.number(args, "limit", DEFAULT_RESULTS, 1, MAX_RESULTS));
        }

        boolean accepts(Job job) {
            return (category.isEmpty() || category.get().equals(categoryOf(job)))
                    && (location.isEmpty() || mentions(job, location.get()))
                    && (since.isEmpty() || isNewerThan(job, since.get()));
        }
    }

    private static boolean isNewerThan(Job job, Instant since) {
        return job.postedAt() != null && !job.postedAt().isBefore(since);
    }

    private Stream<Job> candidates(Criteria criteria, Map<UUID, MatchResult> matches,
                                   UUID userId) {
        Optional<String> query = criteria.query();
        if (query.isEmpty()) {
            return matches.values().stream().map(MatchResult::job);
        }
        JobSearchFilters filters = new JobSearchFilters(null, null, null, null, null, null, null,
                null, null, null, criteria.category().map(List::of).orElse(null));
        List<Job> byWords = search.searchJobs(
                new JobSearchQuery(query.get(), filters, 0, POOL, null)).jobs();
        return distinct(Stream.concat(byWords.stream(),
                semantic.semanticSearch(userId, query.get(), POOL).stream()));
    }

    /** The first occurrence of each posting, so a hit found both ways keeps its text rank. */
    private static Stream<Job> distinct(Stream<Job> jobs) {
        Set<UUID> seen = new HashSet<>();
        return jobs.filter(job -> seen.add(job.id()));
    }

    private static boolean mentions(Job job, String place) {
        return Stream.of(job.location(), job.municipality(), job.region())
                .anyMatch(field -> field != null && field.toLowerCase(Locale.ROOT).contains(place));
    }

    private static String categoryOf(Job job) {
        return job.jobCategory() == null ? null : job.jobCategory().name();
    }

    private static List<String> categories() {
        return Arrays.stream(JobCategory.values()).map(Enum::name).toList();
    }
}
