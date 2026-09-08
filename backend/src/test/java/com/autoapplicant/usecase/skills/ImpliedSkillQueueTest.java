package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the parsers are allowed to infer, and what gets dropped on the way in. The queue is the
 * only place a parser may read between the lines, so its filters are the whole safeguard.
 */
class ImpliedSkillQueueTest {

    private static final UUID USER = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ParsedSkillSuggestion> stored = new ArrayList<>();

    private final ImpliedSkillQueue queue = new ImpliedSkillQueue(new ParsedSkillSuggestionRepositoryPort() {
        @Override public List<ParsedSkillSuggestion> findByUserId(UUID userId) { return stored; }
        @Override public void record(ParsedSkillSuggestion s) {
            if (stored.stream().noneMatch(e -> e.normalizedName().equals(s.normalizedName()))) stored.add(s);
        }
        @Override public void remove(UUID userId, String normalizedName) {
            stored.removeIf(s -> s.normalizedName().equals(normalizedName));
        }
    });

    @Test
    void an_inference_backed_by_a_line_of_the_document_is_queued() {
        queue(json("""
                {"impliedSkills":[{"skill":"Scrum","evidence":"Ran fortnightly retrospectives"}]}"""),
                List.of());

        assertThat(stored).singleElement().satisfies(s -> {
            assertThat(s.skillName()).isEqualTo("Scrum");
            assertThat(s.normalizedName()).isEqualTo("scrum");
            assertThat(s.evidence()).isEqualTo("Ran fortnightly retrospectives");
        });
    }

    @Test
    void an_inference_with_no_evidence_is_dropped() {
        // An unarguable suggestion gets clicked through rather than read.
        queue(json("{\"impliedSkills\":[{\"skill\":\"Scrum\"},{\"skill\":\"Kanban\",\"evidence\":\"  \"}]}"),
                List.of());

        assertThat(stored).isEmpty();
    }

    @Test
    void an_inference_the_document_already_stated_outright_is_dropped() {
        queue(json("""
                {"impliedSkills":[{"skill":"scrum","evidence":"Ran retrospectives"}]}"""),
                List.of("Scrum"));

        assertThat(stored).isEmpty();
    }

    @Test
    void a_document_cannot_queue_more_than_six_inferences() {
        StringBuilder sb = new StringBuilder("{\"impliedSkills\":[");
        for (int i = 0; i < 12; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"skill\":\"Skill").append(i).append("\",\"evidence\":\"line ").append(i).append("\"}");
        }
        queue(json(sb.append("]}").toString()), List.of());

        assertThat(stored).hasSize(6);
    }

    @Test
    void a_response_with_no_implied_skills_at_all_is_not_an_error() {
        queue(json("{\"skills\":[\"Java\"]}"), List.of("Java"));

        assertThat(stored).isEmpty();
    }

    private void queue(JsonNode node, List<String> alreadyStated) {
        queue.queue(USER, node, alreadyStated, ParsedSkillSuggestion.Source.CV_PARSE);
    }

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
