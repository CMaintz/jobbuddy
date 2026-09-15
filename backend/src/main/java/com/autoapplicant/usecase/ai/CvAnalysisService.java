package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.AiAnalysisResult;
import com.autoapplicant.domain.document.CvVersion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.GenerationGuardrails;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Reads a CV or the master career profile against an optional target posting and returns a
 * structured fit-and-risk verdict. A reading surface, not a writing one: it carries the
 * untrusted-input guard and the market's posting-reading rules, but no honesty/cliche framing and
 * no output content guards, because its output is a JSON verdict the candidate never sends.
 *
 * <p>The framing comes from the same {@link GenerationGuardrails} envelope every other surface uses.
 */
@Service
public class CvAnalysisService implements AnalyzeCvUseCase {

    private static final Logger log = LoggerFactory.getLogger(CvAnalysisService.class);

    /** The analyst persona — a fresh ATS reviewer/coach that never assumes unstated skills. */
    private static final String ANALYST_PERSONA =
            "You are an expert ATS reviewer and career coach. Analyze CVs and respond with JSON only. "
            + "Never assume skills or experience the CV does not state.";

    private final ChatProviderPort aiProvider;
    private final JobRepositoryPort jobRepo;
    private final CvVersionRepositoryPort cvRepo;
    private final CareerProfileContextService careerProfileContext;
    private final AnalysisResponseParser analysisParser;

    public CvAnalysisService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                             JobRepositoryPort jobRepo,
                             CvVersionRepositoryPort cvRepo,
                             CareerProfileContextService careerProfileContext,
                             AnalysisResponseParser analysisParser) {
        this.aiProvider = aiProvider;
        this.jobRepo = jobRepo;
        this.cvRepo = cvRepo;
        this.careerProfileContext = careerProfileContext;
        this.analysisParser = analysisParser;
    }

    @Override
    @Async("userAiTaskExecutor")
    public CompletableFuture<AiAnalysisResult> analyze(UUID userId, UUID cvVersionId,
                                                       UUID jobId, String rawJobDescription) {
        try {
            Job job = jobId != null ? jobRepo.findById(jobId).orElse(null) : null;
            String jobDesc = job != null ? job.descriptionClean() : rawJobDescription;
            PromptComposition composition = analysisComposition(
                    resolveCvContent(userId, cvVersionId), jobDesc, job != null ? job.country() : null);
            String response =
                    AiResponseParser.sanitize(aiProvider.generateJson(composition, AiOperations.CV_ANALYSIS));
            return CompletableFuture.completedFuture(analysisParser.parse(response));
        } catch (Exception e) {
            log.error("CV analysis failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Explicit CV version wins; otherwise the PII-free master profile JSON. */
    private String resolveCvContent(UUID userId, UUID cvVersionId) {
        return cvVersionId != null
                ? cvRepo.findById(cvVersionId).map(CvVersion::content).orElse("")
                : careerProfileContext.buildJson(userId);
    }

    /**
     * The analysis prompt as a reading-surface composition: the untrusted-input guard and the
     * market's posting-reading rules, but no honesty/cliche framing — the output is a JSON verdict.
     */
    private PromptComposition analysisComposition(String cvContent, String jobDesc, String jobCountry) {
        GenerationGuardrails guardrails = GenerationGuardrails.forMedium(
                GenerationGuardrails.Medium.ANALYSIS, null, jobDesc, jobCountry);
        String prompt = buildAnalysisPrompt(cvContent, jobDesc, guardrails.marketRules());
        String system = ANALYST_PERSONA + "\n\n" + guardrails.untrustedInputBlock();
        return new PromptComposition(system, prompt, "", "", "", "", prompt);
    }

    private static String buildAnalysisPrompt(String cvContent, String jobDescription, String marketRules) {
        return jobDescription != null && !jobDescription.isBlank()
                ? targetedAnalysisPrompt(cvContent, jobDescription, marketRules)
                : generalAnalysisPrompt(cvContent);
    }

    /** CV judged against a specific posting: keyword/experience/gap analysis plus a separate risk verdict. */
    private static String targetedAnalysisPrompt(String cvContent, String jobDescription, String marketRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze this CV / career profile and provide specific, actionable feedback.\n\n");
        sb.append("## CV Content\n").append(cvContent).append("\n\n");
        sb.append("## Target Job Description\n").append(jobDescription).append("\n\n");
        sb.append("Judge the CV against THIS job: ATS keyword matching, experience alignment, and gaps.\n");
        if (!marketRules.isBlank()) {
            sb.append('\n').append(marketRules).append('\n');
        }
        sb.append("""

                Respond with ONLY a JSON object in exactly this shape:
                {
                  "score": <0-100 overall score>,
                  "summary": "<2-3 sentence overall verdict>",
                  "strengths": ["<what already works well>", ...],
                  "gaps": ["<missing keywords, weak areas, or misalignments>", ...],
                  "suggestions": ["<concrete, actionable improvement — one per entry>", ...],
                  "dimensions": {
                    "technicalSkills": <0-100 — required/preferred skills coverage>,
                    "experience": <0-100 — work-history domain and role-type alignment>,
                    "cultureFit": <0-100 — company culture signals vs the candidate's profile>,
                    "careerAlignment": <0-100 — growth path and motivation fit for this role>,
                    "location": "PASS|FLAG|FAIL — commute/remote/relocation feasibility",
                    "locationNote": "<one sentence explaining the location verdict, or null>"
                  },
                  "risk": {
                    "legitimacy": "HIGH_CONFIDENCE|CAUTION|SUSPICIOUS - is this a real, active opening?",
                    "legitimacyNote": "<one sentence on the legitimacy verdict>",
                    "signals": [{"label":"<short risk label>","severity":"LOW|MEDIUM|HIGH","note":"<one sentence>"}],
                    "compensationReliability": "HIGH|MEDIUM|LOW|UNKNOWN - trust in advertised pay as real base",
                    "compensationNote": "<one sentence, or null>"
                  }
                }
                Assess "risk" (posting legitimacy, risk signals, compensation reliability) SEPARATELY
                from the score - it must NEVER change the score or any dimension. Surface signals,
                never accuse; note legitimate explanations. Use ghost-posting cues (stale or vague
                posting, contradictory or unrealistic requirements, no concrete team or role detail),
                and classify how far the advertised comp is trustworthy base pay vs variable / "up to" /
                commission. Give 0-4 risk signals; omit the array if none.
                Score each dimension independently; do not average them yourself.
                Be honest about gaps — never assume skills the CV does not state.
                Give 3-6 entries per list. Every suggestion must be actionable, not generic advice.""");
        return sb.toString();
    }

    /** CV judged on general strength when no posting is given. */
    private static String generalAnalysisPrompt(String cvContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze this CV / career profile and provide specific, actionable feedback.\n\n");
        sb.append("## CV Content\n").append(cvContent).append("\n\n");
        sb.append("No target job given — judge the CV on general strength: clarity, quantified achievements, ATS readiness.\n");
        sb.append("""

                Respond with ONLY a JSON object in exactly this shape:
                {
                  "score": <0-100 overall score>,
                  "summary": "<2-3 sentence overall verdict>",
                  "strengths": ["<what already works well>", ...],
                  "gaps": ["<missing keywords, weak areas, or misalignments>", ...],
                  "suggestions": ["<concrete, actionable improvement — one per entry>", ...]
                }
                Give 3-6 entries per list. Every suggestion must be actionable, not generic advice.""");
        return sb.toString();
    }
}
