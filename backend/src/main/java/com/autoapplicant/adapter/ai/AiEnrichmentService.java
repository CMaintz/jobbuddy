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
                Analyze this job posting and return JSON with these fields:
                {
                  "aiSummary": "2-3 sentence summary",
                  "aiTags": ["tag1", "tag2"],
                  "aiSeniorityEstimate": "JUNIOR|MID|SENIOR|LEAD|PRINCIPAL|EXECUTIVE"
                }

                Job title: %s
                Company: %s
                Description: %s
                """.formatted(
                job.title(),
                job.companyName() != null ? job.companyName() : "Unknown",
                job.descriptionClean() != null ? job.descriptionClean().substring(0, Math.min(2000, job.descriptionClean().length())) : ""
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
            List<String> tags = (List<String>) parsed.getOrDefault("aiTags", List.of());
            String seniority = (String) parsed.get("aiSeniorityEstimate");

            return new Job(job.id(), job.source(), job.sourceJobId(), job.url(), job.title(),
                    job.companyId(), job.companyName(), job.descriptionRaw(), job.descriptionClean(),
                    job.employmentType(), job.seniority(), job.remoteType(),
                    job.location(), job.municipality(), job.region(), job.country(),
                    job.salaryMin(), job.salaryMax(), job.currency(),
                    job.technologies(), job.skills(), job.languages(),
                    job.postedAt(), job.scrapedAt(),
                    summary, tags, seniority,
                    job.duplicateGroupId(), job.isActive(), job.createdAt(), job.updatedAt());
        } catch (Exception e) {
            log.warn("Failed to parse AI enrichment response: {}", e.getMessage());
            return job;
        }
    }
}
