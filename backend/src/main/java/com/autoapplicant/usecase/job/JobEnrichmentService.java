package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.port.in.job.EnrichJobUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class JobEnrichmentService implements EnrichJobUseCase {

    private static final Logger log = LoggerFactory.getLogger(JobEnrichmentService.class);
    private final AiProviderPort aiProvider;
    private final ObjectMapper objectMapper;

    public JobEnrichmentService(@Qualifier("enrichmentAiProvider") AiProviderPort aiProvider, ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    private static final int MAX_ENRICHMENT_RETRIES = 3;
    private static final java.util.regex.Pattern RETRY_DELAY_PATTERN =
            java.util.regex.Pattern.compile("retry in ([0-9]+(?:\\.[0-9]+)?)s");

    @Async("aiTaskExecutor")
    public CompletableFuture<Job> enrich(Job job) {
        for (int attempt = 0; attempt < MAX_ENRICHMENT_RETRIES; attempt++) {
            try {
                String prompt = buildEnrichmentPrompt(job);
                com.autoapplicant.domain.document.PromptComposition composition =
                        new com.autoapplicant.domain.document.PromptComposition(
                                "You are a job data enrichment assistant. Respond only with JSON.",
                                prompt, "", "", "", "", prompt
                        );
                String response = aiProvider.generate(composition);
                return CompletableFuture.completedFuture(applyEnrichment(job, response));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                long retryMs = parseRetryDelayMs(msg);
                if (retryMs > 0 && attempt < MAX_ENRICHMENT_RETRIES - 1) {
                    log.warn("AI enrichment rate-limited for job {} (attempt {}/{}), waiting {}ms",
                            job.id(), attempt + 1, MAX_ENRICHMENT_RETRIES, retryMs);
                    try {
                        Thread.sleep(retryMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.warn("AI enrichment failed for job {}: {}", job.id(), msg);
                    break;
                }
            }
        }
        return CompletableFuture.completedFuture(job);
    }

    /** Parses the Gemini "Please retry in 46.7s" hint from a 429 error message. */
    private long parseRetryDelayMs(String errorMessage) {
        java.util.regex.Matcher m = RETRY_DELAY_PATTERN.matcher(errorMessage);
        if (m.find()) {
            return (long) (Double.parseDouble(m.group(1)) * 1000) + 1000; // +1s buffer
        }
        if (errorMessage.contains("429") || errorMessage.contains("RESOURCE_EXHAUSTED")
                || errorMessage.contains("quota")) {
            return 60_000L; // conservative fallback
        }
        return 0;
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
                  "municipality": "primary Danish municipality name or null",
                  "jobCategory": "SOFTWARE_IT|DATA_ANALYTICS|DESIGN_UX|MARKETING|SALES|FINANCE|HR|ENGINEERING|OPERATIONS_LOGISTICS|CUSTOMER_SERVICE|LEGAL|HEALTHCARE|MANAGEMENT|EDUCATION|CREATIVE_MEDIA|OTHER"
                }

                Rules:
                - technologies: specific tools, languages, frameworks, libraries, platforms, cloud services
                - skills: soft skills, methodologies, domain competencies (NOT technologies)
                - Only include salary if numbers are explicitly stated in the posting
                - municipality: match to a known Danish kommune name (e.g. "København", "Aarhus", "Odense")
                - jobCategory: choose the single best-fit category. Use OTHER only if nothing fits.

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

            List<String> mergedTech = !technologies.isEmpty() ? technologies : job.technologies();
            List<String> mergedSkills = !skills.isEmpty() ? skills : job.skills();

            String municipality = job.municipality() != null ? job.municipality()
                    : (String) parsed.get("municipality");

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

            // AI category fallback: only apply if programmatic classifier returned null or OTHER
            JobCategory jobCategory = job.jobCategory();
            if (jobCategory == null || jobCategory == JobCategory.OTHER) {
                String rawCategory = (String) parsed.get("jobCategory");
                if (rawCategory != null) {
                    try { jobCategory = JobCategory.valueOf(rawCategory); }
                    catch (IllegalArgumentException ignored) {}
                }
            }

            return new Job(job.id(), job.source(), job.sourceJobId(), job.url(), job.title(),
                    job.companyId(), job.companyName(), job.descriptionRaw(), job.descriptionClean(),
                    employmentType, job.seniority(), remoteType,
                    job.location(), municipality, job.region(), job.country(),
                    salaryMin, salaryMax, currency,
                    mergedTech, mergedSkills, job.languages(),
                    job.postedAt(), job.scrapedAt(),
                    summary, tags, aiSeniority,
                    job.duplicateGroupId(), job.isActive(), jobCategory, job.createdAt(), job.updatedAt());
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
