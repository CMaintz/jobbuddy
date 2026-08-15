package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.SkillGapReport;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.user.ProfileEmbedding;
import com.autoapplicant.port.in.ai.AnalyzeSkillGapsUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The "upskill" analysis: what skills does the market the user is actually
 * pursuing demand, and where does the profile fall short?
 *
 * Corpus = the user's pipeline (saved + applied jobs) plus the nearest
 * embedding-matched market jobs, so the answer reflects both explicit interest
 * and the roles the matching engine considers reachable.
 */
@Service
public class SkillGapService implements AnalyzeSkillGapsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SkillGapService.class);

    private static final int MAX_PIPELINE_JOBS = 15;
    private static final int MAX_MATCHED_JOBS = 10;
    private static final int DESCRIPTION_CHARS = 1_200;

    private final ChatProviderPort aiProvider;
    private final CareerProfileContextService careerProfileContext;
    private final SavedJobRepositoryPort savedJobRepo;
    private final ApplicationRepositoryPort applicationRepo;
    private final JobRepositoryPort jobRepo;
    private final JobEmbeddingRepositoryPort jobEmbeddingRepo;
    private final ProfileEmbeddingRepositoryPort profileEmbeddingRepo;
    private final ObjectMapper objectMapper;

    public SkillGapService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                           CareerProfileContextService careerProfileContext,
                           SavedJobRepositoryPort savedJobRepo,
                           ApplicationRepositoryPort applicationRepo,
                           JobRepositoryPort jobRepo,
                           JobEmbeddingRepositoryPort jobEmbeddingRepo,
                           ProfileEmbeddingRepositoryPort profileEmbeddingRepo,
                           ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.careerProfileContext = careerProfileContext;
        this.savedJobRepo = savedJobRepo;
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.jobEmbeddingRepo = jobEmbeddingRepo;
        this.profileEmbeddingRepo = profileEmbeddingRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<SkillGapReport> analyzeSkillGaps(UUID userId) {
        try {
            List<Job> jobs = collectCorpus(userId);
            if (jobs.isEmpty()) {
                return CompletableFuture.completedFuture(
                        new SkillGapReport(List.of(), "No jobs to analyze yet — save roles or apply to a few first.", 0));
            }

            String profileJson = careerProfileContext.buildJson(userId);
            String prompt = buildPrompt(profileJson, jobs);
            PromptComposition composition = new PromptComposition(
                    "You are a career development analyst. Compare a candidate profile against real job "
                    + "postings and identify concrete skill gaps. Never flag skills the profile already covers. "
                    + "Respond with ONLY valid JSON.",
                    prompt, "", "", "", "", prompt);

            JsonNode root = objectMapper.readTree(
                    AiResponseParser.extractJsonObject(aiProvider.generateJson(composition)));

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

    /** Pipeline jobs first (explicit interest), then embedding-matched market jobs. */
    private List<Job> collectCorpus(UUID userId) {
        Set<UUID> ids = new LinkedHashSet<>();
        applicationRepo.findByUserId(userId).stream()
                .map(Application::jobId)
                .filter(java.util.Objects::nonNull)
                .limit(MAX_PIPELINE_JOBS)
                .forEach(ids::add);
        savedJobRepo.findJobIdsByUserId(userId).stream()
                .limit(MAX_PIPELINE_JOBS)
                .forEach(ids::add);

        profileEmbeddingRepo.findByUserId(userId)
                .map(ProfileEmbedding::embedding)
                .ifPresent(vector -> jobEmbeddingRepo
                        .findNearestNeighborJobIds(vector, MAX_MATCHED_JOBS)
                        .forEach(ids::add));

        return jobRepo.findByIds(new ArrayList<>(ids)).stream()
                .filter(j -> j.descriptionClean() != null && !j.descriptionClean().isBlank())
                .limit(MAX_PIPELINE_JOBS + MAX_MATCHED_JOBS)
                .toList();
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
