package com.autoapplicant.usecase.linkedin;

import com.autoapplicant.domain.linkedin.LinkedInQueryPlan;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.linkedin.GenerateLinkedInQueryPlanUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.linkedin.LinkedInQueryPlanRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a candidate profile into a breadth-appropriate set of LinkedIn search
 * keywords via the LLM. Runs rarely — on profile change or when a plan goes stale —
 * never in the recurring crawl hot path, so the crawl itself stays fully deterministic.
 *
 * <p>Privacy: the prompt is built only from non-identity profile fields (headline,
 * summary, skills, technologies, languages). It never sees name/email/phone/URLs,
 * honouring the same invariant as {@code CareerProfileForAi}.
 */
@Service
public class LinkedInQueryPlanService implements GenerateLinkedInQueryPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(LinkedInQueryPlanService.class);

    private final ChatProviderPort aiProvider;
    private final ProfileRepositoryPort profileRepo;
    private final LinkedInQueryPlanRepositoryPort planRepo;
    private final ObjectMapper objectMapper;

    /** Regenerate a plan once it is older than this many days. */
    @Value("${app.linkedin.plan.staleness-days:14}")
    private int stalenessDays;

    /** Hard ceiling on keywords the planner may emit for one profile. */
    @Value("${app.linkedin.plan.max-keywords:30}")
    private int maxKeywords;

    /** Default breadth: narrow | normal | wide. Wide lets the model cast a broad net for generalist fields. */
    @Value("${app.linkedin.plan.default-breadth:wide}")
    private String defaultBreadth;

    public LinkedInQueryPlanService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                                    ProfileRepositoryPort profileRepo,
                                    LinkedInQueryPlanRepositoryPort planRepo,
                                    ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.profileRepo = profileRepo;
        this.planRepo = planRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    public LinkedInQueryPlan generateForUser(UUID userId) {
        Profile profile = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No profile for user " + userId));
        return generateFor(profile);
    }

    @Override
    public List<LinkedInQueryPlan> ensureFreshPlans() {
        List<LinkedInQueryPlan> current = new ArrayList<>();
        for (Profile profile : profileRepo.findAll()) {
            try {
                var existing = planRepo.findByUserId(profile.userId());
                boolean stale = existing.isEmpty()
                        || existing.get().keywords().isEmpty()
                        || existing.get().generatedAt() == null
                        || Duration.between(existing.get().generatedAt(), Instant.now())
                                .compareTo(Duration.ofDays(stalenessDays)) > 0;
                if (stale) {
                    log.info("LinkedIn: (re)generating query plan for user {}", profile.userId());
                    current.add(generateFor(profile));
                } else {
                    current.add(existing.get());
                }
            } catch (Exception e) {
                log.warn("LinkedIn: query-plan generation failed for user {}: {} — keeping any existing plan",
                        profile.userId(), e.getMessage());
                planRepo.findByUserId(profile.userId()).ifPresent(current::add);
            }
        }
        return current;
    }

    private LinkedInQueryPlan generateFor(Profile profile) {
        String breadth = defaultBreadth;
        PromptComposition composition = new PromptComposition(
                "You are a job-search strategist. Given a candidate profile, output a set of LinkedIn "
                + "job-search keyword queries that cover the full breadth of roles the candidate could "
                + "realistically pursue.\n"
                + "Danish employers post in both Danish and English, often for the same role, and a "
                + "query in one language does not return the other. Where the search covers Denmark, "
                + "include the Danish title alongside the English one when it is genuinely used "
                + "(\"udvikler\" and \"developer\", \"projektleder\" and \"project manager\") — but do not "
                + "invent a Danish title nobody advertises, which returns nothing and wastes a query.\n"
                + "Respond with ONLY valid JSON.",
                buildPrompt(profile, breadth), "", "", "", "", buildPrompt(profile, breadth));

        List<String> keywords;
        try {
            JsonNode root = objectMapper.readTree(
                    AiResponseParser.extractJsonObject(aiProvider.generateJson(composition)));
            keywords = readKeywords(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LinkedIn keyword plan from AI response", e);
        }
        if (keywords.isEmpty()) {
            throw new IllegalStateException("AI returned no keywords for user " + profile.userId());
        }

        LinkedInQueryPlan plan = new LinkedInQueryPlan(
                null, profile.userId(), keywords, breadth, Instant.now());
        LinkedInQueryPlan saved = planRepo.save(plan);
        log.info("LinkedIn: saved {}-keyword plan (breadth={}) for user {}",
                keywords.size(), breadth, profile.userId());
        return saved;
    }

    /** De-dupes case-insensitively, trims blanks, and caps at the configured ceiling. */
    private List<String> readKeywords(JsonNode root) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (JsonNode k : root.path("keywords")) {
            String term = k.asText("").trim();
            if (term.isEmpty()) continue;
            if (seen.add(term.toLowerCase())) {
                out.add(term);
                if (out.size() >= maxKeywords) break;
            }
        }
        return out;
    }

    private String buildPrompt(Profile p, String breadth) {
        String breadthGuidance = switch (breadth == null ? "" : breadth.toLowerCase()) {
            case "narrow" -> "Keep it tight: only the candidate's core specialisation. Aim for 5-8 keywords.";
            case "normal" -> "Cover the core field plus clearly adjacent roles. Aim for 10-15 keywords.";
            default -> "Cast a WIDE net across every role the candidate could plausibly do, including "
                     + "adjacent and generalist titles. If the field is broad, use most of the budget; "
                     + "if it is narrow and specialised, do not pad with irrelevant terms.";
        };

        StringBuilder sb = new StringBuilder("""
                Produce LinkedIn job-search keyword queries for the candidate below.

                Rules:
                - Output PLAIN keyword terms only — job titles, role names, specialisations.
                  No boolean operators (no AND/OR), no quotes, no location, no seniority filters.
                - Provide terms in BOTH Danish and English where a field commonly uses both
                  (this is the Danish market). E.g. "marketing" and "markedsføring".
                - Each keyword is one search a person would actually type.
                - Order from most-central to most-peripheral to the candidate's fit.
                - Absolute maximum %d keywords.
                %s

                Return ONLY valid JSON in exactly this shape:
                { "keywords": ["<term>", "<term>", ...] }

                ## Candidate profile (contact-free)
                """.formatted(maxKeywords, breadthGuidance));

        if (notBlank(p.headline())) sb.append("Headline: ").append(p.headline()).append('\n');
        if (notBlank(p.summary())) sb.append("Summary: ").append(p.summary()).append('\n');
        if (p.yearsExperience() != null) sb.append("Years of experience: ").append(p.yearsExperience()).append('\n');
        appendList(sb, "Skills", p.skills());
        appendList(sb, "Technologies", p.technologies());
        appendList(sb, "Languages", p.languages());
        return sb.toString();
    }

    private static void appendList(StringBuilder sb, String label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            sb.append(label).append(": ").append(String.join(", ", values)).append('\n');
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
