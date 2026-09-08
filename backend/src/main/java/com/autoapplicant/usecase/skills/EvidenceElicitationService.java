package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.skill.EvidenceDraft;
import com.autoapplicant.domain.skill.EvidenceGap;
import com.autoapplicant.port.in.skills.ElicitEvidenceUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.autoapplicant.usecase.ai.AiOperations;

/**
 * The one place a model is involved in evidence elicitation, and it is deliberately small: it
 * phrases the question and restructures the answer. It never decides which skills to ask about
 * (the taxonomy and the market do that) and it never adds a fact (the user does that).
 *
 * <p>Both operations degrade to something useful on failure — a template question, and the user's
 * own text in the action field — because a model outage should cost polish, not the feature.
 */
@Service
public class EvidenceElicitationService implements ElicitEvidenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvidenceElicitationService.class);

    /** One call covers a batch; more than this and the questions start to blur together. */
    private static final int MAX_QUESTIONS_PER_CALL = 5;

    private final ChatProviderPort aiProvider;
    private final CareerProfileContextService careerProfileContext;
    private final DocumentFactGuard factGuard;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.evidence-elicitation.enabled:true}")
    private boolean enabled;

    public EvidenceElicitationService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                                      CareerProfileContextService careerProfileContext,
                                      DocumentFactGuard factGuard,
                                      ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.careerProfileContext = careerProfileContext;
        this.factGuard = factGuard;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<EvidenceGap> tailorQuestions(UUID userId, List<EvidenceGap> gaps) {
        if (!enabled || gaps == null || gaps.isEmpty()) return gaps != null ? gaps : List.of();
        List<EvidenceGap> batch = gaps.stream().limit(MAX_QUESTIONS_PER_CALL).toList();

        try {
            String system = """
                    You write one short follow-up question per skill, to draw a specific incident out \
                    of a candidate. A good question asks where and what changed; a bad one asks them \
                    to describe their experience, which produces adjectives.
                    Rules: one question per skill, under 20 words, in the same language as the profile. \
                    Ask only about the skill given. Never imply the candidate did something — you do \
                    not know what they did, which is why you are asking. Respond with ONLY valid JSON.""";
            // No untrusted-input note here: unlike generation, this prompt carries only the
            // user's own profile, and warning about a "## Job Description" section that is
            // not present would be noise.

            StringBuilder user = new StringBuilder("## Candidate profile (for context only)\n")
                    .append(careerProfileContext.buildJson(userId))
                    .append("\n\n## Skills to ask about\n");
            batch.forEach(gap -> user.append("- ").append(gap.skillName()).append('\n'));
            user.append("""

                    Return only valid JSON in exactly this shape:
                    {"questions": [{"skill": "<skill exactly as given>", "question": "<the question>"}]}""");

            JsonNode root = objectMapper.readTree(AiResponseParser.extractJsonObject(
                    AiResponseParser.sanitize(aiProvider.generateJson(
                            new PromptComposition(system, user.toString(), "", "", "", "", user.toString()),
                            AiOperations.SKILL_EVIDENCE))));

            List<EvidenceGap> tailored = new ArrayList<>();
            for (EvidenceGap gap : batch) {
                String question = null;
                for (JsonNode node : root.path("questions")) {
                    if (gap.skillName().equalsIgnoreCase(node.path("skill").asText(null))) {
                        String candidate = node.path("question").asText(null);
                        if (candidate != null && !candidate.isBlank()) question = candidate.strip();
                        break;
                    }
                }
                tailored.add(question != null
                        ? new EvidenceGap(gap.skillName(), gap.marketFrequency(), question)
                        : gap);
            }
            return tailored;
        } catch (Exception e) {
            // The template question is a perfectly good question; it is just less specific.
            log.warn("Question tailoring failed, keeping templates: {}", e.getMessage());
            return batch;
        }
    }

    @Override
    public EvidenceDraft draftFromAnswer(UUID userId, String skillName, String freeText) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("A draft needs the skill it is evidence for");
        }
        if (freeText == null || freeText.isBlank()) {
            throw new IllegalArgumentException("Nothing to draft from");
        }
        String skill = skillName.strip();
        if (!enabled) return rawDraft(skill, freeText);

        try {
            String system = """
                    You reorganise a candidate's own account of their work into three fields. You are \
                    a formatter, not a writer.
                    Rules, in order of importance:
                    1. Use ONLY facts present in the candidate's text. Add nothing — no numbers, no \
                    company names, no outcomes they did not state.
                    2. Do not embellish. "Made it faster" must not become "dramatically improved".
                    3. Keep their words where you can; shorten rather than rewrite.
                    4. Leave a field null when they did not say it. An empty field is correct and \
                    useful; an invented one is not.
                    Respond with ONLY valid JSON.""";

            String user = "## Skill\n" + skill + "\n\n## What the candidate wrote\n" + freeText
                    + """

                    Return only valid JSON in exactly this shape:
                    {"situation": "<where and when, or null>",
                     "action": "<what they did, or null>",
                     "result": "<what was different afterwards, or null>"}""";

            JsonNode root = objectMapper.readTree(AiResponseParser.extractJsonObject(
                    AiResponseParser.sanitize(aiProvider.generateJson(
                            new PromptComposition(system, user, "", "", "", "", user),
                            AiOperations.SKILL_EVIDENCE))));

            String situation = text(root, "situation");
            String action = text(root, "action");
            String result = text(root, "result");
            if (action == null && result == null) return rawDraft(skill, freeText);

            // The model was told to add nothing. This is where that is checked rather than trusted:
            // any figure in the draft must appear in what the user actually wrote.
            String draftText = String.join(" ", orEmpty(situation), orEmpty(action), orEmpty(result));
            DocumentFactGuard.FactAudit audit = factGuard.audit(draftText, freeText);
            List<String> unsupported = new ArrayList<>(audit.inventedMetrics());
            // Against the user's own answer, both tiers mean the same thing: the draft says
            // something they did not.
            unsupported.addAll(audit.unverifiedMetrics());
            if (!unsupported.isEmpty()) {
                log.warn("Evidence draft added figures the answer did not contain: {}", unsupported);
            }
            return new EvidenceDraft(skill, situation, action, result, unsupported);
        } catch (Exception e) {
            log.warn("Evidence drafting failed, keeping the raw answer: {}", e.getMessage());
            return rawDraft(skill, freeText);
        }
    }

    /** The user's own words, unstructured — always better than losing what they typed. */
    private static EvidenceDraft rawDraft(String skill, String freeText) {
        return new EvidenceDraft(skill, null, freeText.strip(), null, List.of());
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value.strip();
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
