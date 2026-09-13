package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.SkillGapReport;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.ai.AnalyzeSkillGapsUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.JobLanguageDetector;
import com.autoapplicant.usecase.document.MarketConventions;
import com.autoapplicant.usecase.job.MarketCorpusService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * The "upskill" analysis: what skills does the market the user is actually
 * pursuing demand, and where does the profile fall short?
 *
 * Corpus = the user's pipeline (saved + applied jobs) plus the nearest
 * embedding-matched market jobs, so the answer reflects both explicit interest
 * and the roles the matching engine considers reachable.
 */
@Service("marketSkillGapService")
public class SkillGapService implements AnalyzeSkillGapsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SkillGapService.class);

    private static final int DESCRIPTION_CHARS = 1_200;

    private final ChatProviderPort aiProvider;
    private final CareerProfileContextService careerProfileContext;
    private final MarketCorpusService marketCorpus;
    private final ObjectMapper objectMapper;

    public SkillGapService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                           CareerProfileContextService careerProfileContext,
                           MarketCorpusService marketCorpus,
                           ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.careerProfileContext = careerProfileContext;
        this.marketCorpus = marketCorpus;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("userAiTaskExecutor")
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "objectMapper.readTree throws the checked JsonProcessingException; the "
                    + "catch also absorbs runtime failures so any analysis error fails the "
                    + "returned future rather than escaping the async executor.")
    public CompletableFuture<SkillGapReport> analyzeSkillGaps(UUID userId) {
        try {
            List<Job> jobs = marketCorpus.collect(userId, true);
            if (jobs.isEmpty()) {
                return CompletableFuture.completedFuture(
                        new SkillGapReport(List.of(), "No jobs to analyze yet — save roles or apply to a few first.", 0));
            }

            String profileJson = careerProfileContext.buildJson(userId);
            // Read the corpus the way a local recruiter would: a missing "det er en fordel" is an
            // opportunity, not a gap, and reporting it as one sends the candidate off to learn
            // something nobody required.
            String marketRules = MarketConventions.jobReadingRules(MarketConventions.resolve(
                    JobLanguageDetector.detect(jobs.isEmpty() ? null : jobs.getFirst().descriptionClean()),
                    jobs.isEmpty() ? null : jobs.getFirst().country()));
            String prompt = buildPrompt(profileJson, jobs)
                    + (marketRules.isBlank() ? "" : "\n\n" + marketRules);
            PromptComposition composition = new PromptComposition(
                    "You are a career development analyst. Compare a candidate profile against real job "
                    + "postings and identify concrete skill gaps. Never flag skills the profile already covers. "
                    + "Respond with ONLY valid JSON.",
                    prompt, "", "", "", "", prompt);

            JsonNode root = objectMapper.readTree(
                    AiResponseParser.extractJsonObject(aiProvider.generateJson(composition, AiOperations.SKILL_GAP)));

            List<SkillGapReport.SkillGap> gaps = new ArrayList<>();
            for (JsonNode g : root.path("gaps")) {
                String skill = g.path("skill").asText(null);
                if (skill == null || skill.isBlank()) continue;
                List<String> resources = new ArrayList<>();
                g.path("resources").forEach(r -> resources.add(r.asText()));
                gaps.add(new SkillGapReport.SkillGap(
                        skill,
                        g.path("demand").asInt(1),
                        g.path("priority").asText("MEDIUM"),
                        g.path("why").asText(null),
                        resources));
            }
            return CompletableFuture.completedFuture(new SkillGapReport(
                    gaps, root.path("summary").asText(null), jobs.size()));
        } catch (Exception e) {
            log.error("Skill-gap analysis failed for user {}: {}", userId, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private static String buildPrompt(String profileJson, List<Job> jobs) {
        StringBuilder sb = new StringBuilder("""
                Compare the candidate profile against the job postings below. Identify the skills and
                technologies the postings demand that the profile does not credibly cover.

                Return only valid JSON in exactly this shape:
                {
                  "summary": "<2-3 sentence overall read on where the candidate stands vs this market>",
                  "gaps": [{
                    "skill": "<the missing skill or technology>",
                    "demand": <integer — roughly how many of the postings ask for it>,
                    "priority": "HIGH|MEDIUM|LOW",
                    "why": "<one sentence on why it matters for these roles>",
                    "resources": ["<2-3 concrete ways to close the gap: course topic, project idea, documentation>"]
                  }]
                }
                Give 4-8 gaps ordered by priority. Base demand counts on the postings provided, not general knowledge.

                ## Candidate profile (contact-free)
                """).append(profileJson);

        sb.append("\n\n## Job postings (").append(jobs.size()).append(")\n");
        for (Job job : jobs) {
            String desc = job.descriptionClean();
            sb.append("\n### ").append(job.title());
            if (job.companyName() != null) sb.append(" — ").append(job.companyName());
            sb.append('\n').append(desc, 0, Math.min(DESCRIPTION_CHARS, desc.length())).append('\n');
        }
        return sb.toString();
    }
}
