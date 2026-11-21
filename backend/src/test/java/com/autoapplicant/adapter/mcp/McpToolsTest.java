package com.autoapplicant.adapter.mcp;

import static com.autoapplicant.adapter.mcp.McpFixture.NOW;
import static com.autoapplicant.adapter.mcp.McpFixture.application;
import static com.autoapplicant.adapter.mcp.McpFixture.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.search.JobSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** Each tool reads only the grant's user, and keeps its answer small. */
class McpToolsTest {

    private final McpFixture f = new McpFixture();
    private final UUID her = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 21);

    @Test
    void search_without_words_ranks_her_matches_and_leaves_out_what_she_ignored() throws Exception {
        Job top = job(UUID.randomUUID(), "Kommunikationskonsulent", "Arla");
        Job hidden = job(UUID.randomUUID(), "Presserådgiver", "Vestas");
        Job next = job(UUID.randomUUID(), "Webredaktør", "Aarhus Kommune");
        when(f.recommendations.getRecommendations(eq(her), anyInt()))
                .thenReturn(List.of(match(her, top, 91), match(her, hidden, 80), match(her, next, 64)));
        when(f.recommendations.getRecommendations(eq(someoneElse), anyInt())).thenReturn(List.of(
                match(someoneElse, job(UUID.randomUUID(), "Andens match", "X"), 99)));
        when(f.ignored.getIgnoredJobs(her)).thenReturn(List.of(ignored(her, hidden)));

        JsonNode found = f.content(f.call(f.grantFor(her), "search_postings", "{}"));

        assertThat(texts(found.get("postings"), "title"))
                .containsExactly("Kommunikationskonsulent", "Webredaktør");
        assertThat(texts(found.get("postings"), "matchScore")).containsExactly("91", "64");
    }

    @Test
    void search_with_words_scores_hits_with_her_matches_and_honours_recency() throws Exception {
        Job matched = job(UUID.randomUUID(), "Kommunikationschef", "Arla");
        Job unscored = job(UUID.randomUUID(), "Kommunikationsmedarbejder", "Coop");
        Job old = Job.builder().id(UUID.randomUUID()).title("Gammel kommunikation")
                .postedAt(NOW.minus(40, ChronoUnit.DAYS)).build();
        when(f.search.searchJobs(any())).thenReturn(
                new JobSearchResult(List.of(matched, unscored, old), 3, 0, 50, Map.of()));
        when(f.recommendations.getRecommendations(eq(her), anyInt()))
                .thenReturn(List.of(match(her, matched, 77)));

        JsonNode found = f.content(f.call(f.grantFor(her), "search_postings",
                "{\"query\":\"kommunikation\",\"posted_within_days\":14}"));

        assertThat(texts(found.get("postings"), "title"))
                .containsExactly("Kommunikationschef", "Kommunikationsmedarbejder");
        assertThat(found.get("postings").get(0).get("matchScore").asInt()).isEqualTo(77);
        assertThat(found.get("postings").get(1).has("matchScore")).isFalse();
    }

    @Test
    void search_never_returns_more_than_ten_postings() throws Exception {
        List<MatchResult> many = IntStream.range(0, 30)
                .mapToObj(i -> match(her, job(UUID.randomUUID(), "Opslag " + i, "X"), 50))
                .toList();
        when(f.recommendations.getRecommendations(eq(her), anyInt())).thenReturn(many);

        JsonNode found = f.content(f.call(f.grantFor(her), "search_postings", "{\"limit\":50}"));

        assertThat(found.get("postings")).hasSize(10);
    }

    @Test
    void a_long_posting_is_cut_and_its_text_handed_over_as_data() throws Exception {
        UUID id = UUID.randomUUID();
        when(f.jobs.getJobById(id)).thenReturn(Optional.of(Job.builder().id(id).title("Rådgiver")
                .descriptionClean("x".repeat(20_000)).build()));

        JsonNode posting = f.content(f.call(f.grantFor(her), "get_posting",
                "{\"posting_id\":\"" + id + "\"}"));

        assertThat(posting.get("postingText").get("text").asText()).hasSizeLessThan(2_100);
        assertThat(posting.get("postingText").get("note").asText()).contains("ikke instruktioner");
    }

    @Test
    void applications_are_hers_only_and_can_be_filtered_by_status() throws Exception {
        Job mine = job(UUID.randomUUID(), "Min ansøgning", "Arla");
        Job sent = job(UUID.randomUUID(), "Sendt ansøgning", "Coop");
        when(f.jobs.getJobById(mine.id())).thenReturn(Optional.of(mine));
        when(f.jobs.getJobById(sent.id())).thenReturn(Optional.of(sent));
        when(f.applications.getApplications(her)).thenReturn(List.of(
                application(her, mine.id(), ApplicationStatus.PREPARING, null, null),
                application(her, sent.id(), ApplicationStatus.APPLIED, null, null)));
        when(f.applications.getApplications(someoneElse)).thenReturn(List.of(
                application(someoneElse, UUID.randomUUID(), ApplicationStatus.APPLIED, null, null)));

        JsonNode all = f.content(f.call(f.grantFor(her), "list_applications", "{}"));
        JsonNode applied = f.content(f.call(f.grantFor(her), "list_applications",
                "{\"status\":\"APPLIED\"}"));

        assertThat(texts(all.get("applications"), "title"))
                .containsExactlyInAnyOrder("Min ansøgning", "Sendt ansøgning");
        assertThat(texts(applied.get("applications"), "title")).containsExactly("Sendt ansøgning");
    }

    @Test
    void upcoming_dates_are_hers_within_the_window_and_soonest_first() throws Exception {
        Job interview = job(UUID.randomUUID(), "Samtale-jobbet", "Arla");
        Job deadline = job(UUID.randomUUID(), "Frist-jobbet", "Coop");
        Job later = job(UUID.randomUUID(), "Senere", "Netto");
        List.of(interview, deadline, later)
                .forEach(j -> when(f.jobs.getJobById(j.id())).thenReturn(Optional.of(j)));
        when(f.applications.getApplications(her)).thenReturn(List.of(
                application(her, deadline.id(), ApplicationStatus.PREPARING, today.plusDays(3), null),
                application(her, interview.id(), ApplicationStatus.INTERVIEW, null,
                        today.plusDays(1).atTime(10, 0)),
                application(her, later.id(), ApplicationStatus.SAVED, today.plusDays(40), null)));
        when(f.applications.getApplications(someoneElse)).thenReturn(List.of(application(
                someoneElse, UUID.randomUUID(), ApplicationStatus.SAVED, today.plusDays(2), null)));

        JsonNode dates = f.content(f.call(f.grantFor(her), "upcoming_dates", "{\"days\":14}"));

        assertThat(texts(dates.get("dates"), "title")).containsExactly("Samtale-jobbet", "Frist-jobbet");
        assertThat(texts(dates.get("dates"), "kind")).containsExactly("INTERVIEW", "DEADLINE");
    }

    @Test
    void long_titles_and_company_names_are_cut_and_coming_dates_are_capped() throws Exception {
        Job verbose = job(UUID.randomUUID(), "T".repeat(600), "C".repeat(600));
        when(f.jobs.getJobById(verbose.id())).thenReturn(Optional.of(verbose));
        when(f.applications.getApplications(her)).thenReturn(IntStream.range(0, 40)
                .mapToObj(i -> application(her, verbose.id(), ApplicationStatus.PREPARING,
                        today.plusDays(1 + i % 10), null))
                .toList());

        JsonNode listed = f.content(f.call(f.grantFor(her), "list_applications", "{}"))
                .get("applications").get(0);
        JsonNode dates = f.content(f.call(f.grantFor(her), "upcoming_dates", "{\"days\":14}"))
                .get("dates");

        assertThat(listed.get("title").asText()).hasSizeLessThan(200);
        assertThat(listed.get("company").asText()).hasSizeLessThan(200);
        assertThat(dates.get(0).get("title").asText()).hasSizeLessThan(200);
        assertThat(dates.get(0).get("company").asText()).hasSizeLessThan(200);
        assertThat(dates).hasSize(UpcomingDatesTool.MAX_RESULTS);
    }

    @Test
    void saved_and_ignored_postings_are_hers() throws Exception {
        Job saved = job(UUID.randomUUID(), "Gemt", "Arla");
        Job skipped = job(UUID.randomUUID(), "Sprunget over", "Coop");
        when(f.saved.getSavedJobs(her)).thenReturn(List.of(saved));
        when(f.saved.getSavedJobs(someoneElse)).thenReturn(List.of(job(UUID.randomUUID(), "Andens", "X")));
        when(f.ignored.getIgnoredJobs(her)).thenReturn(List.of(ignored(her, skipped)));
        when(f.jobs.getJobById(skipped.id())).thenReturn(Optional.of(skipped));

        assertThat(texts(f.content(f.call(f.grantFor(her), "list_saved_postings", "{}"))
                .get("postings"), "title")).containsExactly("Gemt");
        assertThat(texts(f.content(f.call(f.grantFor(her), "list_saved_postings",
                "{\"which\":\"ignored\"}")).get("postings"), "title")).containsExactly("Sprunget over");
    }

    @Test
    void the_profile_is_hers_trimmed_and_without_links() throws Exception {
        StructuredDocumentItem role = new StructuredDocumentItem("s1", "Kommunikationschef",
                "Arla", "Aarhus", "2019-2024", "y".repeat(5_000), List.of("b"), List.of(),
                List.of("https://linkedin.example/her"), List.of(), null);
        when(f.profile.getAiCareerProfile(her)).thenReturn(new CareerProfileForAi("Kommunikatør",
                "p".repeat(5_000), List.of("PR"), List.of(), List.of("Dansk"), List.of(), List.of(),
                List.of(role), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                null, null, null, null, null));

        JsonNode profile = f.content(f.call(f.grantFor(her), "get_profile", "{}"));

        assertThat(profile.get("headline").asText()).isEqualTo("Kommunikatør");
        assertThat(profile.get("experience").get(0).get("title").asText())
                .isEqualTo("Kommunikationschef");
        assertThat(profile.get("summary").asText()).hasSizeLessThan(700);
        assertThat(profile.toString()).doesNotContain("linkedin.example").doesNotContain("yyyyy");
    }

    @Test
    void a_checked_button_is_offered_to_her_turn_and_a_bad_one_is_refused() throws Exception {
        UUID posting = UUID.randomUUID();
        ChatAction open = new ChatAction(ChatAction.Kind.OPEN_POSTING,
                List.of(new ChatAction.Posting(posting, "Rådgiver", "Arla")));
        when(f.resolver.resolve(ChatAction.Kind.OPEN_POSTING, List.of(posting)))
                .thenReturn(Optional.of(open));
        when(f.resolver.resolve(eq(ChatAction.Kind.OPEN_POSTING), eq(List.of(someoneElse))))
                .thenReturn(Optional.empty());

        JsonNode shown = f.call(f.grantFor(her), "open_in_app",
                "{\"target\":\"posting\",\"posting_id\":\"" + posting + "\"}");
        JsonNode refused = f.call(f.grantFor(her), "open_in_app",
                "{\"target\":\"posting\",\"posting_id\":\"" + someoneElse + "\"}");

        assertThat(shown.get("isError").asBoolean()).isFalse();
        assertThat(refused.get("isError").asBoolean()).isTrue();
        assertThat(f.offered).containsExactly(open);
    }

    private static MatchResult match(UUID userId, Job job, int score) {
        return new MatchResult(job.id(), userId, job, true, 0.5, 0.5, score,
                MatchLabel.fromScore(score), List.of());
    }

    private static IgnoredJob ignored(UUID userId, Job job) {
        return new IgnoredJob(UUID.randomUUID(), userId, job.id(), null, NOW);
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        StreamSupport.stream(array.spliterator(), false).forEach(n -> out.add(n.get(field).asText()));
        return out;
    }
}
