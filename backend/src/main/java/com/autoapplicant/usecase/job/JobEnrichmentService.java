package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.JobContact;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.JobText;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import com.autoapplicant.port.in.job.EnrichJobUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobEnrichmentService implements EnrichJobUseCase {

    private static final Logger log = LoggerFactory.getLogger(JobEnrichmentService.class);
    private final ChatProviderPort aiProvider;
    private final ObjectMapper objectMapper;

    public JobEnrichmentService(@Qualifier("enrichmentChatProvider") ChatProviderPort aiProvider,
                                ObjectMapper objectMapper) {
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
                You are given the text of a job posting page, one numbered line per line. The content \
                may include page chrome (company boilerplate, cookie notices, navigation links) mixed in \
                with the actual job description.

                Return JSON with exactly these fields:
                {
                  "boilerplateLines": [[12, 18], [40, 40]],
                  "shortDescription": "1-2 sentence teaser capturing the role and its key appeal, for use in job card previews",
                  "aiSummary": "2-3 sentence human-friendly summary",
                  "aiTags": ["tag1", "tag2", "tag3"],
                  "aiSeniorityEstimate": "JUNIOR|MID|SENIOR|LEAD|PRINCIPAL|EXECUTIVE",
                  "technologies": ["Java", "React", "PostgreSQL"],
                  "skills": ["Agile", "Communication", "Problem Solving"],
                  "requiredSkills": ["Java"],
                  "preferredSkills": ["Kubernetes"],
                  "requirements": [{"text": "5 års erfaring med backend-udvikling", "tier": "REQUIRED", "kind": "EXPERIENCE", "skill": null},
                                   {"text": "Java", "tier": "REQUIRED", "kind": "SKILL", "skill": "Java"}],
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
                - boilerplateLines: line ranges [start, end] (1-based, inclusive, as numbered in the \
                content below) holding text that is NOT part of this job posting — cookie notices, \
                navigation, legal footers, generic company branding blurb, and other vacancies listed \
                on the same page. Do NOT rewrite or return the posting text; only point at what to cut. \
                Never mark requirements, responsibilities, qualifications, contact names, email \
                addresses, phone numbers, or application instructions. Return [] when nothing is \
                boilerplate — an empty list is a better answer than a guess.
                - shortDescription: ignore any navigation text, cookie banners, or other page chrome
                - technologies: specific tools, languages, frameworks, libraries, platforms, cloud services
                - skills: soft skills, methodologies, domain competencies (NOT technologies)
                - requiredSkills / preferredSkills: the SAME asks as technologies+skills, re-sorted by \
                how the posting phrases them. Requirements read "du skal", "det er et krav", "du har \
                X \u00e5rs erfaring med", or a bare "du har"; preferences read "det er en fordel", "gerne", \
                "vi ser gerne at", "erfaring med X er et plus", "kendskab til". English postings use \
                "must have"/"required" versus "nice to have"/"a plus"/"bonus". Put an ask in \
                requiredSkills only when the posting's own wording demands it \u2014 when the phrasing is \
                ambiguous, it is a preference. Use the same short label you used in technologies/skills \
                so the two lists line up. Leave either list empty rather than guessing.
                - Only include salary if numbers are explicitly stated in the posting
                - municipality: match to a known Danish kommune name (e.g. "København", "Aarhus", "Odense")
                - applicationDeadline: the stated application deadline (e.g. "Ansøgningsfrist"); null when not stated or "as soon as possible"
                - contact: the person the posting names to answer questions about the role (Danish                 postings usually do: "Har du spørgsmål, så kontakt …"). Copy the name, their stated job                 title, and any email or phone given FOR THAT PERSON. Use null for the whole "contact"                 object when the posting names no individual — a generic jobs@ address or "HR" is NOT a                 contact person. Never guess a name, never carry one over from page chrome or another                 vacancy on the page.
                - requirements: EVERY ask the posting makes, in the posting's own words — do not \
                shorten to a label and do not skip one because it is not a technology. Include \
                years of experience, education, languages, certifications, driving licences, \
                willingness to travel. tier is REQUIRED when the posting demands it and PREFERRED \
                otherwise (the same wording rules as requiredSkills/preferredSkills). kind is one \
                of SKILL, EXPERIENCE, EDUCATION, LANGUAGE, CERTIFICATION, OTHER. Set "skill" only \
                for kind SKILL, to the same short label you used in technologies/skills; null \
                otherwise. This list is NOT filtered against those lists — it is the full picture.
                - jobCategory: choose the single best-fit category. Use OTHER only if nothing fits.

                Job title: %s
                Company: %s
                Content: %s
                """.formatted(
                job.title(),
                job.companyName() != null ? job.companyName() : "Unknown",
                JobText.numbered(JobText.truncate(job.descriptionClean()))
        );
    }

    @SuppressWarnings("unchecked")
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "objectMapper.readValue throws the checked JsonProcessingException; the "
                    + "catch also absorbs runtime failures (bad casts on untrusted model output) "
                    + "so a malformed response returns the un-enriched job.")
    // Package-private so the contact-extraction rules can be driven with realistic model
    // responses without a live provider.
    Job applyEnrichment(Job job, String jsonResponse) {
        try {
            String cleaned = sanitizeJsonResponse(jsonResponse);
            Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<>() {});

            // The model points at boilerplate; the cut happens here, so the stored description
            // stays the posting's own words rather than the model's rendering of them.
            String descriptionClean = JobText.stripLines(job.descriptionClean(), lineRanges(parsed));

            String shortDescription = job.shortDescription() != null ? job.shortDescription()
                    : (String) parsed.get("shortDescription");

            String summary = (String) parsed.get("aiSummary");
            List<String> tags = getList(parsed, "aiTags");
            String aiSeniority = (String) parsed.get("aiSeniorityEstimate");

            List<String> technologies = getList(parsed, "technologies");
            List<String> skills = getList(parsed, "skills");

            List<String> mergedTech = !technologies.isEmpty() ? technologies : job.technologies();
            List<String> mergedSkills = !skills.isEmpty() ? skills : job.skills();

            // Requirement tier. Only ever narrowed to what the flat lists already hold, so a model
            // that invents a requirement out of nowhere cannot make the matcher penalise for it.
            List<String> vocabulary = new java.util.ArrayList<>(mergedTech);
            vocabulary.addAll(mergedSkills);
            List<String> required = confineToVocabulary(getList(parsed, "requiredSkills"), vocabulary);
            // An ask cannot be both. The stricter tier wins, so "nice to have" never softens a demand.
            List<String> preferred = confineToVocabulary(getList(parsed, "preferredSkills"), vocabulary)
                    .stream()
                    .filter(cand -> required.stream().noneMatch(r -> r.equalsIgnoreCase(cand)))
                    .toList();
            // Nothing usable came back — keep whatever a previous enrichment established.
            boolean tiersUsable = !required.isEmpty() || !preferred.isEmpty();
            List<String> requiredSkills  = tiersUsable ? required  : job.requiredSkills();
            List<String> preferredSkills = tiersUsable ? preferred : job.preferredSkills();

            // Deliberately not confined to the technologies/skills vocabulary: that narrowing
            // protects the matcher from an invented requirement, and would here throw away every
            // ask that is not a short label — which is most of what a posting demands.
            List<JobRequirement> requirements = parseRequirements(parsed);
            if (requirements.isEmpty()) requirements = job.requirements();

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
                contact = toContactPerson(rawContact);
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
                    .requiredSkills(requiredSkills).preferredSkills(preferredSkills)
                    .requirements(requirements)
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
        // Fences come off with the shared helper; what follows are repairs specific to the
        // enrichment schema, which no other caller needs.
        String cleaned = com.autoapplicant.usecase.document.AiResponseParser
                .stripCodeFence(raw.trim());

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

    /**
     * The extracted contact, or null when what came back is not a person.
     *
     * <p>The prompt already says a shared inbox is not a contact, but a model will sometimes hand
     * one back anyway, and a letter addressed to "jobs@" is worse than one addressed to nobody. So
     * the rule is enforced here too: with no name, the contact must offer a channel that reaches a
     * human — a direct phone number counts, a role address does not.
     */
    private static JobContact toContactPerson(Map<?, ?> raw) {
        JobContact contact = JobContact.ofNullable(
                str(raw, "name"), str(raw, "title"), str(raw, "email"), str(raw, "phone"));
        if (contact == null) return null;
        if (contact.hasName()) return contact;
        boolean reachesAPerson = contact.phone() != null
                || (contact.email() != null && !isRoleAddress(contact.email()));
        return reachesAPerson ? contact : null;
    }

    /** Local parts that address a function rather than a person. */
    private static final java.util.Set<String> ROLE_ADDRESS_LOCAL_PARTS = java.util.Set.of(
            "job", "jobs", "hr", "career", "careers", "karriere", "rekruttering", "recruitment",
            "recruiting", "ansoegning", "ansøgning", "ansogning", "info", "kontakt", "contact",
            "mail", "post", "office", "admin", "hello", "hej", "apply", "application");

    private static boolean isRoleAddress(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return true;   // not an address we can reason about; treat as unusable
        String local = email.substring(0, at).toLowerCase(java.util.Locale.ROOT);
        // "jobs.dk", "hr-denmark" and similar decorations still address a function.
        String base = local.split("[.\\-_+]")[0];
        return ROLE_ADDRESS_LOCAL_PARTS.contains(local) || ROLE_ADDRESS_LOCAL_PARTS.contains(base);
    }

    /** A string field from a nested JSON object, or null when absent or not a string. */
    private static String str(Map<?, ?> map, String key) {
        return map.get(key) instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * The tier list, keeping only entries the flat technology/skill lists already contain.
     *
     * <p>The tiers decide how hard a missing ask counts against a candidate, so an entry that
     * exists only in the tier list is a claim nothing else corroborates. Matching on a
     * case-insensitive exact label also keeps the tier vocabulary identical to what the profile
     * is compared against — a tier entry the matcher could never match is worse than none.
     */
    private static List<String> confineToVocabulary(List<String> tier, List<String> vocabulary) {
        if (tier.isEmpty() || vocabulary.isEmpty()) return List.of();
        return tier.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> vocabulary.stream().filter(v -> v.equalsIgnoreCase(s)).findFirst().orElse(null))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> parsed, String key) {
        Object val = parsed.get(key);
        return val instanceof List<?> list ? (List<String>) list : List.of();
    }

    /**
     * Reads the {@code boilerplateLines} ranges. Anything malformed is skipped rather
     * than failing the enrichment: a bad range should cost us a surviving cookie
     * banner, not every field the model got right.
     */
    /**
     * Reads the posting's asks. A malformed entry is skipped rather than failing the
     * enrichment — losing one requirement beats losing every other field the model got right.
     */
    @SuppressWarnings("unchecked")
    /**
     * A posting asking for more than this is listing wishes; the cap keeps one verbose ad from
     * dominating every prompt it is pasted into.
     */
    private static final int MAX_REQUIREMENTS = 30;

    /** Longer than this is a paragraph the model failed to split, not a single ask. */
    private static final int MAX_REQUIREMENT_CHARS = 300;

    private List<JobRequirement> parseRequirements(Map<String, Object> parsed) {
        if (!(parsed.get("requirements") instanceof List<?> raw)) return List.of();
        List<JobRequirement> result = new java.util.ArrayList<>();
        for (Object entry : raw) {
            if (result.size() >= MAX_REQUIREMENTS) break;
            if (!(entry instanceof Map<?, ?> row)) continue;
            Object text = row.get("text");
            if (!(text instanceof String t) || t.isBlank()) continue;
            String cleaned = t.strip();
            if (cleaned.length() > MAX_REQUIREMENT_CHARS) {
                cleaned = cleaned.substring(0, MAX_REQUIREMENT_CHARS).strip();
            }
            result.add(new JobRequirement(cleaned,
                    RequirementTier.parse(asString(row.get("tier"))),
                    RequirementKind.parse(asString(row.get("kind"))),
                    asString(row.get("skill"))));
        }
        return List.copyOf(result);
    }

    private static String asString(Object value) {
        return value instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    private List<int[]> lineRanges(Map<String, Object> parsed) {
        if (!(parsed.get("boilerplateLines") instanceof List<?> raw)) return List.of();
        List<int[]> ranges = new java.util.ArrayList<>();
        for (Object entry : raw) {
            if (!(entry instanceof List<?> pair) || pair.size() < 2) continue;
            if (pair.get(0) instanceof Number from && pair.get(1) instanceof Number to) {
                ranges.add(new int[]{from.intValue(), to.intValue()});
            }
        }
        return ranges;
    }
}
