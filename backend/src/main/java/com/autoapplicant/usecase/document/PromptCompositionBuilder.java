package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.*;
import org.springframework.stereotype.Service;

/**
 * Assembles {@link PromptComposition} objects for all AI generation paths.
 *
 * <p>Each structured-generation method (CV tailoring, application docs) accepts an optional
 * {@link PromptTemplate}. When provided, the template's {@code systemPrompt} and
 * {@code userPrompt} fields supply the AI persona and style guidance. The JSON output schema
 * is always appended by this class — it is never part of a user-editable template, ensuring
 * the response shape stays stable regardless of user customisation.
 */
@Service
public class PromptCompositionBuilder {

    // ── Template-based prose path (legacy / PromptController) ──────────────────────────────

    public PromptComposition compose(PromptCompositionRequest req) {
        String baseSystem = req.template().systemPrompt() != null
                ? req.template().systemPrompt() : defaultApplicationSystemPrompt();
        String systemPrompt = appendLanguage(baseSystem, req.targetLanguage());
        String cvContext = req.contactFreeCareerProfileJson() != null && !req.contactFreeCareerProfileJson().isBlank()
                ? "## Contact-Free Master Career Profile JSON\n" + req.contactFreeCareerProfileJson()
                : "";
        String jobDescText = req.job() != null ? req.job().descriptionClean()
                : (req.rawJobDescription() != null && !req.rawJobDescription().isBlank()
                    ? req.rawJobDescription() : null);
        String jobDesc = jobDescText != null ? "## Job Description\n" + jobDescText : "";
        String styleMemory = buildStyleMemory(req.writingProfile());
        String outputConstraints = req.template().outputConstraints() != null
                ? req.template().outputConstraints() : "";

        String finalPrompt = String.join("\n\n",
                req.template().userPrompt(), cvContext, jobDesc, styleMemory, outputConstraints
        ).trim();

        return new PromptComposition(systemPrompt, req.template().userPrompt(), cvContext,
                jobDesc, styleMemory, outputConstraints, finalPrompt);
    }

    // ── Structured application document path (cover letter, recruiter msg, etc.) ───────────

    /**
     * Builds a prompt for generating a structured application document (cover letter,
     * application text, recruiter message, follow-up message).
     *
     * <p>The {@code styleTemplate} — if provided — supplies the AI persona via its
     * {@code systemPrompt} and style guidance via its {@code userPrompt}. The JSON output
     * schema is always appended regardless of the template.
     */
    public PromptComposition composeStructuredApplicationPrompt(
            String documentType,
            String careerProfileJson,
            String jobDescription,
            String customInstructions,
            String motivationText,
            String targetLanguage,
            PromptTemplate styleTemplate,
            WritingProfile writingProfile) {

        String languageInstruction = targetLanguage != null && !targetLanguage.isBlank()
                ? "Write the document body in " + targetLanguage + "."
                : "Write in the same language as the job description when clear, otherwise Danish.";

        String docLabel = switch (documentType != null ? documentType.toUpperCase() : "") {
            case "COVER_LETTER" -> "a compelling cover letter";
            case "APPLICATION_TEXT" -> "a professional job application text";
            case "RECRUITER_MESSAGE" -> "a brief, personalized recruiter message (under 150 words)";
            case "FOLLOW_UP_MESSAGE" -> "a polite follow-up message (under 100 words)";
            default -> "a professional document";
        };

        // System prompt: template persona if available, otherwise the built-in default
        String baseSystem = styleTemplate != null && styleTemplate.systemPrompt() != null
                ? styleTemplate.systemPrompt()
                : defaultApplicationSystemPrompt();
        String systemPrompt = baseSystem
                + "\nThe career profile context intentionally excludes the user's name, contact "
                + "information, profile image, LinkedIn URL, GitHub URL, and website URL. "
                + "Do not ask for, infer, invent, or output those private identity fields."
                + "\nReturn ONLY valid JSON — no markdown fences, no commentary.\n"
                + languageInstruction;

        // Style guidance from template, if any
        String styleGuidance = styleTemplate != null && styleTemplate.userPrompt() != null
                && !styleTemplate.userPrompt().isBlank()
                ? "\n\n## Style Guidance\n" + styleTemplate.userPrompt()
                : "";

        // Fixed task + JSON schema (never user-editable)
        String schema = """
                {
                  "body": "<full document text>",
                  "keywordCoverage": <0-100 integer>,
                  "matchedKeywords": ["keyword"],
                  "missingKeywords": ["keyword"],
                  "notes": ["1-3 specific observations about gaps or opportunities between the profile and this job — omit if none"]
                }""";

        String styleMemory = buildStyleMemory(writingProfile);

        String userPrompt = "Write " + docLabel + " based on the contact-free master career profile "
                + "and job description provided." + styleGuidance
                + (styleMemory.isBlank() ? "" : "\n\n" + styleMemory)
                + "\n\n" + HONESTY_RULES
                + "\n\nReturn only valid JSON matching exactly this shape:\n" + schema
                + "\n\n## Contact-Free Master Career Profile JSON\n"
                + (careerProfileJson != null ? careerProfileJson : "")
                + "\n\n## Job Description\n"
                + (jobDescription != null ? jobDescription : "(no job description provided)")
                + (customInstructions != null && !customInstructions.isBlank()
                    ? "\n\n## Additional Instructions\n" + customInstructions : "")
                + (motivationText != null && !motivationText.isBlank()
                    ? "\n\n## Applicant's Personal Motivation\n" + motivationText : "");

        return new PromptComposition(systemPrompt, userPrompt, "", "", "", "", userPrompt);
    }

    // ── Structured CV tailoring path ────────────────────────────────────────────────────────

    /**
     * Builds a prompt for structured CV tailoring.
     *
     * <p>The {@code styleTemplate} — if provided — customises the AI's persona and approach.
     * The JSON output schema (mapped to {@link com.autoapplicant.domain.document.structured.TailoredCvContent})
     * is always appended by this method.
     */
    public PromptComposition composeCvTailoringPrompt(
            String careerProfileJson,
            String jobDescription,
            String customInstructions,
            String targetLanguage,
            PromptTemplate styleTemplate,
            WritingProfile writingProfile) {

        String languageInstruction = targetLanguage != null && !targetLanguage.isBlank()
                ? "Write all rewritten text in " + targetLanguage + "."
                : "Write rewritten text in the same language as the job description when clear.";

        String baseSystem = styleTemplate != null && styleTemplate.systemPrompt() != null
                ? styleTemplate.systemPrompt()
                : defaultCvTailoringSystemPrompt();
        String systemPrompt = baseSystem + "\n" + languageInstruction;

        String styleGuidance = styleTemplate != null && styleTemplate.userPrompt() != null
                && !styleTemplate.userPrompt().isBlank()
                ? "\n\n## Style Guidance\n" + styleTemplate.userPrompt()
                : "";

        String schema = """
                {
                  "selectedProfile": "role-specific profile text",
                  "selectedSkills": ["skill"],
                  "experience": [{"sourceId":"<keep existing>","title":"...","subtitle":"...","location":"...","dateRange":"...","description":"...","bullets":["..."],"technologies":["..."],"links":[]}],
                  "projects": [],
                  "education": [],
                  "certifications": [],
                  "keywordCoverage": 0,
                  "matchedKeywords": [],
                  "missingKeywords": [],
                  "notes": ["1-3 specific observations about gaps or opportunities — omit if none"]
                }""";

        String styleMemory = buildStyleMemory(writingProfile);

        String userPrompt = "Tailor the CV content from the contact-free master career profile below "
                + "to best match the job description." + styleGuidance
                + (styleMemory.isBlank() ? "" : "\n\n" + styleMemory)
                + "\n\nRules: use only source facts; you may rewrite profile text, descriptions, "
                + "and bullets, but keep sourceId values unchanged. "
                + "Do not invent employers, titles, dates, schools, credentials, technologies, outcomes, or links."
                + "\n\n" + HONESTY_RULES
                + "\n- When content must be condensed, drop the bullets with the lowest combination of "
                + "relevance to this posting's keywords and uniqueness within the document — not simply "
                + "the oldest ones. A dated bullet that hits posting keywords outranks a recent one that does not."
                + "\n\nReturn only valid JSON matching exactly this shape:\n" + schema
                + "\n\n## Contact-Free Master Career Profile JSON\n"
                + (careerProfileJson != null ? careerProfileJson : "")
                + "\n\n## Job Description\n"
                + (jobDescription != null ? jobDescription : "(no job description provided)")
                + (customInstructions != null && !customInstructions.isBlank()
                    ? "\n\n## Additional Instructions\n" + customInstructions : "");

        return new PromptComposition(systemPrompt, userPrompt, "", "", "", "", userPrompt);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    /** Fixed guardrails appended to every generation prompt — never user-editable. */
    private static final String HONESTY_RULES = """
            ## Honesty & ATS Rules
            - Never fabricate skills, experience, credentials, or outcomes. When the profile lacks a \
            requirement, frame genuinely adjacent experience instead of inventing a match — or leave the \
            gap visible rather than papering over it.
            - Mirror the posting's exact terminology for skills the profile genuinely supports (ATS \
            scanners match literal keywords), but never stuff keywords the profile cannot back up.""";

    private String buildStyleMemory(WritingProfile profile) {
        if (profile == null) return "";
        StringBuilder sb = new StringBuilder();
        if (profile.tone() != null) sb.append("Tone: ").append(profile.tone()).append("\n");
        if (profile.vocabularyNotes() != null)
            sb.append("Vocabulary: ").append(profile.vocabularyNotes()).append("\n");
        if (profile.phrasingPatterns() != null && !profile.phrasingPatterns().isEmpty()) {
            sb.append("Preferred phrases: ")
              .append(String.join(", ", profile.phrasingPatterns())).append("\n");
        }
        return sb.isEmpty() ? "" : "## Writing Style\n" + sb;
    }

    private static String appendLanguage(String systemPrompt, String targetLanguage) {
        if (targetLanguage == null || targetLanguage.isBlank()) return systemPrompt;
        return systemPrompt + "\nAlways write the output in " + targetLanguage + ".";
    }

    private static String defaultApplicationSystemPrompt() {
        return """
                You are an expert career coach and professional writer specialising in job applications.
                Your goal is to help job seekers craft compelling, authentic applications that match their unique voice.
                Always write in a professional yet personal tone. Focus on concrete achievements and relevant experience.
                When the profile includes soft skills (Communication, Leadership, etc.), weave them naturally into achievement
                narratives rather than listing them in a standalone section.
                """;
    }

    private static String defaultCvTailoringSystemPrompt() {
        return """
                You are a careful CV tailoring specialist. Your task is to select and rewrite CV content
                from a structured master career profile so it best matches a specific job description.
                Return JSON only. The profile intentionally excludes the user's name and contact information
                — do not ask for it, infer it, or invent it.
                """;
    }
}
