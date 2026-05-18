package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AiEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(AiEnrichmentService.class);
    private final AiProviderPort aiProvider;
    private final ObjectMapper objectMapper;

    public AiEnrichmentService(AiProviderPort aiProvider, ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<Job> enrich(Job job) {
        try {
            String prompt = buildEnrichmentPrompt(job);
            // Build a simple PromptComposition for the enrichment call
            com.autoapplicant.domain.document.PromptComposition composition =
                    new com.autoapplicant.domain.document.PromptComposition(
                            "You are a job data enrichment assistant. Respond only with JSON.",
                            prompt, "", "", "", "", prompt
                    );
            String response = aiProvider.generate(composition);
            return CompletableFuture.completedFuture(applyEnrichment(job, response));
        } catch (Exception e) {
            log.warn("AI enrichment failed for job {}: {}", job.id(), e.getMessage());
            return CompletableFuture.completedFuture(job);
        }
    }

    private String buildEnrichmentPrompt(Job job) {
        return """
                Analyze this job posting and return JSON with exactly these fields:
                {
                  "aiSummary": "2-3 sentence human-friendly summary",
                  "aiTags": ["tag1", "tag2", "tag3"],
                  "aiSeniorityEstimate": "JUNIOR|MID|SENIOR|LEAD|PRINCIPAL|EXECUTIVE",
                  "technologies": ["Java", "React", "PostgreSQL"],
                  "skills": ["Agile", "Communication", "Problem Solving"],
                  "employmentType": "FULL_TIME|PART_TIME|CONTRACT|FREELANCE|INTERNSHIP or null",
                  "remoteType": "REMOTE|HYBRID|ON_SITE or null",
                  "salaryMin": null,
                  "salaryMax": null,
                  "currency": null,
                  "municipality": "primary Danish municipality name or null"
                }

                Rules:
                - technologies: specific tools, languages, frameworks, libraries, platforms, cloud services
                - skills: soft skills, methodologies, domain competencies (NOT technologies)
                - Only include salary if numbers are explicitly stated in the posting
                - municipality: match to a known Danish kommune name (e.g. "København", "Aarhus", "Odense")

                Job title: %s
                Company: %s
                Description: %s
                """.formatted(
                job.title(),
                job.companyName() != null ? job.companyName() : "Unknown",
                job.descriptionClean() != null ? job.descriptionClean().substring(0, Math.min(3000, job.descriptionClean().length())) : ""
        );
    }

    @SuppressWarnings("unchecked")
    private Job applyEnrichment(Job job, String jsonResponse) {
        try {
            String cleaned = jsonResponse.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```json", "").replaceFirst("```", "").trim();
            }
            Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<>() {});

            String summary = (String) parsed.get("aiSummary");
            List<String> tags = getList(parsed, "aiTags");
            String aiSeniority = (String) parsed.get("aiSeniorityEstimate");

            List<String> technologies = getList(parsed, "technologies");
            List<String> skills = getList(parsed, "skills");

            // Merge: prefer AI-extracted if available, fall back to what was crawled
            List<String> mergedTech = !technologies.isEmpty() ? technologies : job.technologies();
            List<String> mergedSkills = !skills.isEmpty() ? skills : job.skills();

            // Only update location if not already set by the crawler
            String municipality = job.municipality() != null ? job.municipality()
                    : (String) parsed.get("municipality");

            // Parse employment type if crawler didn't populate it
            com.autoapplicant.domain.job.EmploymentType employmentType = job.employmentType();
            if (employmentType == null) {
                String raw = (String) parsed.get("employmentType");
                if (raw != null) {
                    try { employmentType = com.autoapplicant.domain.job.EmploymentType.valueOf(raw); }
                    catch (IllegalArgumentException ignored) {}
                }
            }

            com.autoapplicant.domain.job.RemoteType remoteType = job.remoteType();
            if (remoteType == null) {
                String raw = (String) parsed.get("remoteType");
                if (raw != null) {
                    try { remoteType = com.autoapplicant.domain.job.RemoteType.valueOf(raw); }
                    catch (IllegalArgumentException ignored) {}
                }
            }

            Integer salaryMin = job.salaryMin() != null ? job.salaryMin()
                    : parsed.get("salaryMin") instanceof Number n ? n.intValue() : null;
            Integer salaryMax = job.salaryMax() != null ? job.salaryMax()
                    : parsed.get("salaryMax") instanceof Number n ? n.intValue() : null;
            String currency = job.currency() != null ? job.currency() : (String) parsed.get("currency");

            return new Job(job.id(), job.source(), job.sourceJobId(), job.url(), job.title(),
                    job.companyId(), job.companyName(), job.descriptionRaw(), job.descriptionClean(),
                    employmentType, job.seniority(), remoteType,
                    job.location(), municipality, job.region(), job.country(),
                    salaryMin, salaryMax, currency,
                    mergedTech, mergedSkills, job.languages(),
                    job.postedAt(), job.scrapedAt(),
                    summary, tags, aiSeniority,
                    job.duplicateGroupId(), job.isActive(), job.createdAt(), job.updatedAt());
        } catch (Exception e) {
            log.warn("Failed to parse AI enrichment response: {}", e.getMessage());
            return job;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> parsed, String key) {
        Object val = parsed.get(key);
        return val instanceof List<?> list ? (List<String>) list : List.of();
    }
}
