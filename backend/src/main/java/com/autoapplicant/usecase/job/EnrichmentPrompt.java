package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobText;
import java.util.List;

/**
 * The enrichment prompt, for one posting or a batch. Both share the field schema and the rules,
 * so a batch item is the same extraction as a single call, only addressed by id.
 */
final class EnrichmentPrompt {

    private static final String SINGLE_INTRO = """
                You are given the text of a job posting page, one numbered line per line. The content \
                may include page chrome (company boilerplate, cookie notices, navigation links) mixed in \
                with the actual job description.

                """;

    private static final String SINGLE_RETURN = """
                Return JSON with exactly these fields:
                """;

    private static final String SCHEMA_AND_RULES = """
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
                  "jobCategory": "SOFTWARE_IT|DATA_ANALYTICS|DESIGN_UX|GRC_COMPLIANCE|MARKETING|SALES|FINANCE|HR|ENGINEERING|OPERATIONS_LOGISTICS|CUSTOMER_SERVICE|LEGAL|HEALTHCARE|MANAGEMENT|EDUCATION|CREATIVE_MEDIA|OTHER"
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
                - technologies: specific tools, languages, frameworks, libraries, platforms, cloud services. \
                A non-technical posting often has few or none: a communications role may list only \
                Umbraco and Canva, and its skills carry "Intern kommunikation", "SoMe" or "NIS2".
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
                - jobCategory: choose the single best-fit category. Use OTHER only if nothing fits. \
                GRC_COMPLIANCE is governance, risk and compliance work (informationssikkerhed, NIS2, \
                GDPR, security awareness, beredskab), not hands-on IT security and not guarding.

                """;

    private static final String POSTING = """
                Job title: %s
                Company: %s
                Content: %s
                                """;

    private static final String BATCH_INTRO = """
                You are given several job postings. Each one starts with a line like \
                "=== POSTING P1 ===" giving its id, and its text has one numbered line per line, \
                numbered from 1 within that posting. The content may include page chrome (company \
                boilerplate, cookie notices, navigation links) mixed in with the job description.

                Return one JSON object of the form {"postings": [ ... ]} holding exactly one object \
                per posting, each with an "id" set to that posting's id. Line numbers in \
                boilerplateLines refer to that posting's own numbering. Each object has these fields:
                """;

    private EnrichmentPrompt() {}

    static String single(Job job) {
        return SINGLE_INTRO + SINGLE_RETURN + SCHEMA_AND_RULES + posting(job);
    }

    /** {@code ids} names each posting, in the same order as {@code jobs}. */
    static String batch(List<String> ids, List<Job> jobs) {
        StringBuilder prompt = new StringBuilder(BATCH_INTRO).append(SCHEMA_AND_RULES);
        for (int i = 0; i < jobs.size(); i++) {
            prompt.append("\n=== POSTING ").append(ids.get(i)).append(" ===\n").append(posting(jobs.get(i)));
        }
        return prompt.toString();
    }

    private static String posting(Job job) {
        return POSTING.formatted(
                job.title(),
                job.companyName() != null ? job.companyName() : "Unknown",
                JobText.numbered(JobText.truncate(job.descriptionClean())));
    }
}
