package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.autoapplicant.usecase.skills.ImpliedSkillQueue;
import com.autoapplicant.usecase.skills.ProfileSkillIngestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ParseCvService implements ParseCvUseCase {

    private static final Logger log = LoggerFactory.getLogger(ParseCvService.class);

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
    private final ProfilePrivateInfoRepositoryPort privateInfoRepo;
    private final ProfileSocialRepositoryPort socialRepo;
    private final ImpliedSkillQueue impliedSkills;
    private final ProfileSkillIngestion skillIngestion;

    public ParseCvService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider, ObjectMapper objectMapper,
                          ProfilePrivateInfoRepositoryPort privateInfoRepo,
                          ProfileSocialRepositoryPort socialRepo,
                          ImpliedSkillQueue impliedSkills,
                          ProfileSkillIngestion skillIngestion) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.privateInfoRepo = privateInfoRepo;
        this.socialRepo = socialRepo;
        this.impliedSkills = impliedSkills;
        this.skillIngestion = skillIngestion;
    }

    @Override
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "objectMapper.readTree throws the checked JsonProcessingException; the "
                    + "catch also absorbs runtime failures (NPE etc.) so a bad AI response falls "
                    + "back to an empty profile rather than propagating.")
    public Profile parseCvText(UUID userId, String rawCvText) {
        try {
            PromptComposition composition = new PromptComposition(
                    SYSTEM_PROMPT, rawCvText, null, null, null, null, null);
            String json = aiProvider.generate(composition, AiOperations.CV_PARSE);
            JsonNode node = objectMapper.readTree(AiResponseParser.stripCodeFence(json.trim()));

            savePrivateInfo(userId, node);
            saveSocialLinks(userId, node);
            captureCvSkills(userId, node);

            return new Profile(
                    null, userId,
                    textOrNull(node, "headline"),
                    textOrNull(node, "summary"),
                    null,
                    arrayOrEmpty(node, "languages"),
                    arrayOrEmpty(node, "interests"),
                    null, null, "DKK", null, null, null, null
            );
        } catch (Exception e) {
            log.error("CV parsing failed for user {}: {}", userId, e.getMessage());
            return new Profile(null, userId, null, null, null, List.of(),
                    List.of(), null, null, "DKK", null, null, null, null);
        }
    }

    /** Persist the CV's name and location as private info, merging into any existing record. */
    private void savePrivateInfo(UUID userId, JsonNode node) {
        String fullName = textOrNull(node, "fullName");
        String location = textOrNull(node, "location");
        if (fullName == null && location == null) return;
        ProfilePrivateInfo existing = privateInfoRepo.findByUserId(userId).orElse(null);
        if (existing == null) {
            privateInfoRepo.save(new ProfilePrivateInfo(null, userId,
                    fullName, null, null, location, null, null, null, null));
        } else {
            privateInfoRepo.save(new ProfilePrivateInfo(existing.id(), userId,
                    fullName != null ? fullName : existing.fullName(),
                    existing.phone(), existing.photoUrl(),
                    location != null ? location : existing.location(),
                    existing.municipality(), existing.contactEmail(),
                    existing.createdAt(), null));
        }
    }

    /** Persist any LinkedIn / GitHub / website URLs the CV listed, after the user's existing links. */
    private void saveSocialLinks(UUID userId, JsonNode node) {
        int order = socialRepo.findByUserId(userId).size();
        saveSocialIfPresent(userId, node, "linkedinUrl", "LinkedIn", "linkedin", order);
        saveSocialIfPresent(userId, node, "githubUrl",   "GitHub",   "github",   order + 1);
        saveSocialIfPresent(userId, node, "websiteUrl",  "Website",  "globe",    order + 2);
    }

    /**
     * Capture the CV's skills, in the two ways the profile keeps them: ingest the stated ones
     * straight into the profile's skill rows (same taxonomy link and category as a hand-typed skill,
     * so grouped sections and proficiency-weighted matching work for an imported CV too), and queue
     * the inferred ones as questions — inferences never join the extracted list, so a wrong guess
     * costs one dismissed suggestion rather than a fabricated skill.
     */
    private void captureCvSkills(UUID userId, JsonNode node) {
        List<String> stated = new java.util.ArrayList<>(arrayOrEmpty(node, "skills"));
        stated.addAll(arrayOrEmpty(node, "technologies"));
        impliedSkills.queue(userId, node, stated, ParsedSkillSuggestion.Source.CV_PARSE);
        skillIngestion.ingest(userId, stated);
    }

    private void saveSocialIfPresent(UUID userId, JsonNode node, String field,
                                      String platform, String iconKey, int displayOrder) {
        String url = textOrNull(node, field);
        if (url == null || url.isBlank()) return;
        List<ProfileSocial> existing = socialRepo.findByUserId(userId);
        boolean alreadyExists = existing.stream()
                .anyMatch(s -> iconKey.equalsIgnoreCase(s.iconKey()));
        if (!alreadyExists) {
            socialRepo.save(new ProfileSocial(null, userId, platform, url, null,
                    iconKey, displayOrder, null, null));
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private static List<String> arrayOrEmpty(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || !child.isArray()) return List.of();
        String[] values = new String[child.size()];
        for (int i = 0; i < child.size(); i++) values[i] = child.get(i).asText();
        return Arrays.asList(values);
    }
}
