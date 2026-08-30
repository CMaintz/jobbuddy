package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Files the skills a parsed document demonstrates without naming, as questions for the user.
 *
 * <p>The parsers stay strictly extractive, and that is not a limitation to be worked around: what
 * a parser writes into the profile is what the fact guard later treats as the candidate's own
 * account, so a skill invented at parse time would be permanently "supported" everywhere
 * downstream, including in the check that decides whether a generated letter made something up.
 *
 * <p>But a CV that describes running fortnightly retrospectives and grooming a backlog evidences
 * Scrum with the word nowhere in the document, and leaving that on the floor costs the candidate
 * matches. So the inference is routed here instead of into the profile: it becomes one row in the
 * skill-suggestion queue, alongside the taxonomy-adjacency and market-demand candidates, and the
 * worst case of a wrong guess is one dismissed suggestion rather than a fabricated skill.
 */
@Service
public class ImpliedSkillQueue {

    private static final Logger log = LoggerFactory.getLogger(ImpliedSkillQueue.class);

    /** More than this from one document is padding, not reading. */
    private static final int MAX_PER_DOCUMENT = 6;

    private final ParsedSkillSuggestionRepositoryPort repo;

    public ImpliedSkillQueue(ParsedSkillSuggestionRepositoryPort repo) {
        this.repo = repo;
    }

    /**
     * Queues the "impliedSkills" entries of a parser response.
     *
     * <p>An entry is dropped when it has no evidence — an unarguable suggestion gets clicked
     * through rather than read — or when it names something the document already stated outright,
     * which is a question the candidate has answered by writing it down.
     *
     * @param alreadyStated the skills and technologies the parser extracted verbatim
     */
    public void queue(UUID userId, JsonNode parsed, Collection<String> alreadyStated,
                      ParsedSkillSuggestion.Source source) {
        if (userId == null || parsed == null) return;
        JsonNode implied = parsed.get("impliedSkills");
        if (implied == null || !implied.isArray()) return;

        Set<String> stated = new HashSet<>();
        if (alreadyStated != null) alreadyStated.forEach(v -> stated.add(normalize(v)));

        int queued = 0;
        for (JsonNode entry : implied) {
            if (queued >= MAX_PER_DOCUMENT) break;
            String name = text(entry, "skill");
            String evidence = text(entry, "evidence");
            if (name == null || evidence == null) continue;
            String normalized = normalize(name);
            if (normalized.isEmpty() || stated.contains(normalized)) continue;
            try {
                repo.record(new ParsedSkillSuggestion(null, userId, name.strip(), normalized,
                        evidence.strip(), source));
                stated.add(normalized);
                queued++;
            } catch (Exception e) {
                // A suggestion that fails to file must never cost the user their parsed profile.
                log.warn("Could not queue implied skill '{}' for user {}: {}",
                        name, userId, e.getMessage());
            }
        }
    }

    /** Convenience for callers holding two extracted lists rather than one. */
    public void queue(UUID userId, JsonNode parsed, List<String> skills, List<String> technologies,
                      ParsedSkillSuggestion.Source source) {
        Set<String> stated = new HashSet<>();
        if (skills != null) stated.addAll(skills);
        if (technologies != null) stated.addAll(technologies);
        queue(userId, parsed, stated, source);
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return null;
        String value = child.asText();
        return value != null && !value.isBlank() ? value : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
