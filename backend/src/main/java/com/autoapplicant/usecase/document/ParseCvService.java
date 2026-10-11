package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.common.CvParseException;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ParseCvService implements ParseCvUseCase {

    static final String NO_TEXT_MESSAGE =
            "No text could be read from this CV. If it is a scanned PDF, paste the text instead.";
    static final String AI_UNAVAILABLE_MESSAGE =
            "The CV could not be parsed right now. Please try again in a moment.";
    static final String UNREADABLE_RESPONSE_MESSAGE =
            "The CV could not be turned into a profile. Please try again, or paste the text instead.";

    private static final String DEFAULT_CURRENCY = "DKK";

    private static final String SYSTEM_PROMPT = """
            You are a CV parser. Extract structured career information from the provided CV text.

            Extract only. Never infer, complete, tidy up or translate a claim into a stronger one:
            if the CV does not say it, omit the field. A parser that fills gaps is worse than one
            that leaves them, because everything downstream treats this output as the candidate's
            own account — including the check that decides whether a later document invented a fact.
            An invented skill here becomes permanently "supported" everywhere else.

            Keep the candidate's own wording for titles and skills rather than normalising it; the
            posting-matching downstream compares literal terms.

            Return ONLY valid JSON matching this structure (omit null fields):
            {
              "fullName": "string",
              "headline": "string",
              "summary": "string",
              "location": "string",
              "linkedinUrl": "string",
              "githubUrl": "string",
              "websiteUrl": "string",
              "skills": ["string"],
              "technologies": ["string"],
              "languages": ["string"],
              "interests": ["string"],
              "impliedSkills": [{"skill": "string", "evidence": "string"}]
            }
            "interests" is for leisure interests when the CV lists them — a standard closing section
            on a Danish CV. Omit it when there are none; never guess at them.

            "impliedSkills" is the one place where reading between the lines is wanted, and it is
            kept separate for a reason: nothing in it reaches the profile. Each entry is put to the
            candidate as a question they answer, so the cost of a wrong guess is one dismissed
            suggestion rather than a fabricated skill.

            Put a skill here when the CV describes doing the work but never names the skill — a CV
            that describes running fortnightly retrospectives and grooming a backlog evidences
            Scrum even with the word absent; one that describes building the deployment pipeline
            that ships every merge evidences CI/CD. Rules:
            - "evidence" must quote or closely paraphrase the CV's own line. No line, no entry.
            - Never repeat a skill already in "skills" or "technologies" — those are extracted, and
              a suggestion the candidate has already made is noise.
            - Do not list a skill merely adjacent to one they have. "Uses Java" is not evidence of
              Kotlin. Only what the described work itself demonstrates.
            - Six entries at most. Omit the field entirely rather than padding it.
            """;

    private final ChatProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final ParsedCvWriter writer;

    public ParseCvService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                          ObjectMapper objectMapper, ParsedCvWriter writer) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.writer = writer;
    }

    /**
     * Runs the AI parse outside any transaction, then files the result in one.
     *
     * @throws CvParseException when there is no text, the provider fails, or its answer is not
     *     the expected JSON; nothing is written in any of those cases
     */
    @Override
    public Profile parseCvText(UUID userId, String rawCvText) {
        if (rawCvText == null || rawCvText.isBlank()) {
            throw new CvParseException(NO_TEXT_MESSAGE);
        }
        JsonNode node = toJson(askAi(rawCvText));
        writer.write(userId, node);
        return new Profile(
                null, userId,
                ParsedCvFields.textOrNull(node, "headline"),
                ParsedCvFields.textOrNull(node, "summary"),
                null,
                ParsedCvFields.arrayOrEmpty(node, "languages"),
                ParsedCvFields.arrayOrEmpty(node, "interests"),
                null, null, DEFAULT_CURRENCY, null, null, null, null
        );
    }

    private String askAi(String rawCvText) {
        PromptComposition composition = new PromptComposition(
                SYSTEM_PROMPT, rawCvText, null, null, null, null, null);
        try {
            return aiProvider.generate(composition, AiOperations.CV_PARSE);
        } catch (RuntimeException e) {
            throw new CvParseException(AI_UNAVAILABLE_MESSAGE, e);
        }
    }

    private JsonNode toJson(String aiResponse) {
        if (aiResponse == null) {
            throw new CvParseException(UNREADABLE_RESPONSE_MESSAGE);
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(AiResponseParser.stripCodeFence(aiResponse.trim()));
        } catch (JsonProcessingException e) {
            throw new CvParseException(UNREADABLE_RESPONSE_MESSAGE, e);
        }
        if (node == null || !node.isObject()) {
            throw new CvParseException(UNREADABLE_RESPONSE_MESSAGE);
        }
        return node;
    }
}
