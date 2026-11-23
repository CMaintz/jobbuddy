package com.autoapplicant.usecase.skills;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the suggestions out of an {@link AdjacentSkillPrompt} reply. */
final class AdjacentSkillReply {

    /** One proposed skill and the held skill it follows from. */
    record Suggestion(String skill, String because) {}

    private static final Pattern ANSWER_START = Pattern.compile("\\{\\s*\"suggestions\"");

    /** The suggestion queue's skill_name column; anything longer is prose, not a skill. */
    private static final int MAX_SKILL = 200;

    private AdjacentSkillReply() {}

    /**
     * The suggestions of the last answer in the reply. A model that corrects itself writes a
     * second answer after the first ("Wait, the skills are in English..."), and the correction is
     * the one it means.
     *
     * @return empty when the reply holds no readable answer, which is not the same as an answer
     *     with no suggestions: only the latter means the skill set has been asked about
     */
    static Optional<List<Suggestion>> parse(ObjectMapper json, String reply) {
        int last = lastAnswerStart(reply);
        if (last < 0) return Optional.empty();
        try {
            JsonNode entries = json.readTree(reply.substring(last)).path("suggestions");
            return entries.isArray() ? Optional.of(suggestions(entries)) : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    private static int lastAnswerStart(String reply) {
        if (reply == null) return -1;
        Matcher answer = ANSWER_START.matcher(reply);
        int last = -1;
        while (answer.find()) last = answer.start();
        return last;
    }

    private static List<Suggestion> suggestions(JsonNode entries) {
        List<Suggestion> out = new ArrayList<>();
        entries.forEach(entry -> {
            String skill = entry.path("skill").asText("").strip();
            String because = entry.path("because").asText("").strip();
            if (!skill.isEmpty() && skill.length() <= MAX_SKILL && !because.isEmpty()) {
                out.add(new Suggestion(skill, because));
            }
        });
        return out;
    }
}
