package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.in.user.ParseLinkedInProfileUseCase;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.skills.ImpliedSkillQueue;
import com.autoapplicant.usecase.skills.ProfileSkillIngestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LinkedInProfileParseService implements ParseLinkedInProfileUseCase {

    private static final Logger log = LoggerFactory.getLogger(LinkedInProfileParseService.class);

    private final ChatProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final ImpliedSkillQueue impliedSkills;
    private final ProfileSkillIngestion skillIngestion;

    public LinkedInProfileParseService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                                       ObjectMapper objectMapper,
                                       ImpliedSkillQueue impliedSkills,
                                       ProfileSkillIngestion skillIngestion) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.impliedSkills = impliedSkills;
        this.skillIngestion = skillIngestion;
    }

    @Override
    public String parseProfileFromText(UUID userId, String extractedText) {
        String systemPrompt = "You are a data extraction assistant. Extract structured information "
                + "from a LinkedIn profile PDF export. Extract only what the export states — never "
                + "infer a date, expand an abbreviation, or improve a title. Omit anything the "
                + "document does not contain. What you return becomes the candidate's profile, and "
                + "later checks treat it as their own account of themselves: an invented detail here "
                + "is one nothing downstream can catch. Return ONLY valid JSON, no markdown.\n\n"
                + "\"impliedSkills\" is the single exception, and it is separate because nothing in "
                + "it reaches the profile: each entry is put to the candidate as a question they "
                + "answer, so a wrong guess costs one dismissed suggestion rather than a fabricated "
                + "skill. Put a skill there when an experience description shows the work being done "
                + "but never names the skill — a role described as running fortnightly retrospectives "
                + "and grooming a backlog evidences Scrum with the word absent. Each entry's "
                + "\"evidence\" must quote or closely paraphrase the export's own line; no line, no "
                + "entry. Never repeat something already in \"skills\", and never list a skill merely "
                + "adjacent to one they have — only what the described work itself demonstrates. Six "
                + "at most, and omit the field rather than padding it.";
        String userPrompt = "Extract the following fields from this LinkedIn profile PDF and return as JSON:\n" +
                "{\n" +
                "  \"fullName\": \"\",\n" +
                "  \"headline\": \"\",\n" +
                "  \"summary\": \"\",\n" +
                "  \"location\": \"\",\n" +
                "  \"skills\": [],\n" +
                "  \"experience\": [{\"title\":\"\",\"company\":\"\",\"startDate\":\"\",\"endDate\":\"\",\"description\":\"\"}],\n" +
                "  \"education\": [{\"institution\":\"\",\"degree\":\"\",\"fieldOfStudy\":\"\",\"startDate\":\"\",\"endDate\":\"\"}],\n" +
                "  \"certifications\": [{\"name\":\"\",\"issuer\":\"\",\"issuedAt\":\"\"}],\n" +
                "  \"impliedSkills\": [{\"skill\":\"\",\"evidence\":\"\"}]\n" +
                "}\n\nPDF content:\n" + extractedText;

        PromptComposition composition = new PromptComposition(
                systemPrompt,
                userPrompt,
                "",
                "",
                "",
                "",
                userPrompt
        );

        String json = aiProvider.generate(composition);
        applySkills(userId, json);
        return json;
    }

    /**
     * Puts the export's skills on the profile and queues what it merely implies, then gets out of
     * the way.
     *
     * <p>The raw JSON is what the caller consumes, so this reads it rather than rewriting it, and
     * swallows its own failures: an import that produced a usable profile must not be reported as
     * failed because a skill could not be filed.
     */
    private void applySkills(UUID userId, String json) {
        try {
            String cleaned = AiResponseParser.stripCodeFence(json.trim());
            JsonNode node = objectMapper.readTree(cleaned);
            List<String> stated = new ArrayList<>();
            JsonNode skills = node.get("skills");
            if (skills != null && skills.isArray()) skills.forEach(n -> stated.add(n.asText()));
            impliedSkills.queue(userId, node, stated, ParsedSkillSuggestion.Source.LINKEDIN_PARSE);
            // Extracted skills go onto the profile as rows, taxonomy-resolved and categorised —
            // the same shape a hand-typed skill gets. The raw JSON the caller consumes is
            // unchanged; this is a side effect on the profile, not a rewrite of the response.
            skillIngestion.ingest(userId, stated);
        } catch (Exception e) {
            log.warn("Could not read skills from the LinkedIn export for user {}: {}",
                    userId, e.getMessage());
        }
    }
}
