package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.JobContact;
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
                String response = aiProvider.generateJson(composition);
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
                You are given the text content of a job posting page. The content may include page chrome \
                (company boilerplate, cookie notices, navigation links) mixed in with the actual job description.

                Return JSON with exactly these fields:
                {
                  "descriptionClean": "full job description extracted from the content — keep all requirements, responsibilities, qualifications, and contact details; remove navigation, cookie banners, company branding boilerplate, and legal footer text",
                  "shortDescription": "1-2 sentence teaser capturing the role and its key appeal, for use in job card previews",
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
                  "applicationDeadline": "YYYY-MM-DD or null",
                  "contact": {"name": "...", "title": "...", "email": "...", "phone": "..."},
                  "jobCategory": "SOFTWARE_IT|DATA_ANALYTICS|DESIGN_UX|MARKETING|SALES|FINANCE|HR|ENGINEERING|OPERATIONS_LOGISTICS|CUSTOMER_SERVICE|LEGAL|HEALTHCARE|MANAGEMENT|EDUCATION|CREATIVE_MEDIA|OTHER"
                }

                Rules:
                - descriptionClean: extract the actual job posting content; preserve contact names, email addresses, phone numbers, and application instructions
                - shortDescription: ignore any navigation text, cookie banners, or other page chrome
                - technologies: specific tools, languages, frameworks, libraries, platforms, cloud services
                - skills: soft skills, methodologies, domain competencies (NOT technologies)
                - Only include salary if numbers are explicitly stated in the posting
                - municipality: match to a known Danish kommune name (e.g. "København", "Aarhus", "Odense")
                - applicationDeadline: the stated application deadline (e.g. "Ansøgningsfrist"); null when not stated or "as soon as possible"
                - contact: the person the posting names to answer questions about the role (Danish                 postings usually do: "Har du spørgsmål, så kontakt …"). Copy the name, their stated job                 title, and any email or phone given FOR THAT PERSON. Use null for the whole "contact"                 object when the posting names no individual — a generic jobs@ address or "HR" is NOT a                 contact person. Never guess a name, never carry one over from page chrome or another                 vacancy on the page.
                - jobCategory: choose the single best-fit category. Use OTHER only if nothing fits.

                Job title: %s
                Company: %s
                Content: %s
                """.formatted(
                job.title(),
                job.companyName() != null ? job.companyName() : "Unknown",
                job.descriptionClean() != null ? job.descriptionClean().substring(0, Math.min(5000, job.descriptionClean().length())) : ""
        );
    }

    @SuppressWarnings("unchecked")
    private Job applyEnrichment(Job job, String jsonResponse) {
        try {
            String cleaned = sanitizeJsonResponse(jsonResponse);
            Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<>() {});

            // AI-extracted clean description (replaces the raw page-text version from TextCleaningService)
            String aiDescClean = (String) parsed.get("descriptionClean");
            String descriptionClean = (aiDescClean != null && !aiDescClean.isBlank())
                    ? aiDescClean : job.descriptionClean();

            String shortDescription = job.shortDescription() != null ? job.shortDescription()
                    : (String) parsed.get("shortDescription");

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

            // Contact person: extraction only fills a gap, so a crawler that already parsed one wins.
            JobContact contact = job.contact();
            if (contact == null && parsed.get("contact") instanceof Map<?, ?> rawContact) {
                contact = JobContact.ofNullable(
                        str(rawContact, "name"), str(rawContact, "title"),
                        str(rawContact, "email"), str(rawContact, "phone"));
            }

            // Crawler-provided deadline wins; the AI extraction is the fallback
            java.time.LocalDate applicationDeadline = job.applicationDeadline();
            if (applicationDeadline == null && parsed.get("applicationDeadline") instanceof String rawDeadline) {
                try { applicationDeadline = java.time.LocalDate.parse(rawDeadline); }
                catch (java.time.format.DateTimeParseException ignored) {}
            }

            return job.toBuilder()
                    .descriptionClean(descriptionClean)
                    .employmentType(employmentType)
                    .remoteType(remoteType)
                    .municipality(municipality)
                    .salaryMin(salaryMin).salaryMax(salaryMax).currency(currency)
                    .technologies(mergedTech).skills(mergedSkills)
                    .aiSummary(summary).aiTags(tags).aiSeniorityEstimate(aiSeniority)
                    .jobCategory(jobCategory)
                    .shortDescription(shortDescription)
                    .applicationDeadline(applicationDeadline)
                    .contact(contact)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse AI enrichment response: {}", e.getMessage());
            return job;
        }
    }

    /**
     * Cleans up common AI response artifacts that break JSON parsing:
     * markdown fences, semicolons used instead of commas, trailing commas.
     */
    static String sanitizeJsonResponse(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.trim();

        // Strip markdown code fences
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) cleaned = cleaned.substring(firstNewline + 1);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        // Extract the JSON object if surrounded by extra text
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        // Replace semicolons between JSON entries with commas
        // Matches: "value"; or ]; or }; followed by whitespace and a quote or bracket
        cleaned = cleaned.replaceAll(";(\\s*[\"{}\\[\\]])", ",$1");

        // Remove trailing commas before } or ]
        cleaned = cleaned.replaceAll(",\\s*([}\\]])", "$1");

        return cleaned;
    }

    /** A string field from a nested JSON object, or null when absent or not a string. */
    private static String str(Map<?, ?> map, String key) {
        return map.get(key) instanceof String s && !s.isBlank() ? s : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> parsed, String key) {
        Object val = parsed.get(key);
        return val instanceof List<?> list ? (List<String>) list : List.of();
    }
}
