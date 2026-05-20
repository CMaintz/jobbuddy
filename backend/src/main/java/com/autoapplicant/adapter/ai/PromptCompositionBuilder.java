package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.document.*;
import org.springframework.stereotype.Service;

@Service
public class PromptCompositionBuilder {

    public PromptComposition compose(PromptCompositionRequest req) {
        String baseSystem = req.template().systemPrompt() != null ? req.template().systemPrompt() : defaultSystemPrompt();
        String systemPrompt = req.targetLanguage() != null && !req.targetLanguage().isBlank()
                ? baseSystem + "\nAlways write the output in " + req.targetLanguage() + "."
                : baseSystem;
        String cvContext = req.contactFreeCareerProfileJson() != null && !req.contactFreeCareerProfileJson().isBlank()
                ? "## Contact-Free Master Career Profile JSON\n" + req.contactFreeCareerProfileJson()
                : "";
        String jobDescText = req.job() != null ? req.job().descriptionClean()
                : (req.rawJobDescription() != null && !req.rawJobDescription().isBlank() ? req.rawJobDescription() : null);
        String jobDesc = jobDescText != null ? "## Job Description\n" + jobDescText : "";
        String styleMemory = buildStyleMemory(req.writingProfile());
        String outputConstraints = req.template().outputConstraints() != null ? req.template().outputConstraints() : "";

        String finalPrompt = String.join("\n\n",
                req.template().userPrompt(),
                cvContext,
                jobDesc,
                styleMemory,
                outputConstraints
        ).trim();

        return new PromptComposition(systemPrompt, req.template().userPrompt(), cvContext,
                jobDesc, styleMemory, outputConstraints, finalPrompt);
    }

    private String buildStyleMemory(WritingProfile profile) {
        if (profile == null) return "";
        StringBuilder sb = new StringBuilder("## Writing Style\n");
        if (profile.tone() != null) sb.append("Tone: ").append(profile.tone()).append("\n");
        if (profile.vocabularyNotes() != null) sb.append("Vocabulary: ").append(profile.vocabularyNotes()).append("\n");
        if (profile.phrasingPatterns() != null && !profile.phrasingPatterns().isEmpty()) {
            sb.append("Preferred phrases: ").append(String.join(", ", profile.phrasingPatterns())).append("\n");
        }
        return sb.toString();
    }

    public PromptComposition composeStructuredApplicationPrompt(String documentType,
                                                                String contactFreeCareerProfileJson,
                                                                String jobDescription,
                                                                String customInstructions,
                                                                String targetLanguage) {
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

        String systemPrompt = """
                You are an expert career coach and professional writer specializing in job applications.
                The career profile context intentionally excludes the user's name, contact information, profile image,
                LinkedIn URL, GitHub URL, and website URL. Do not ask for, infer, invent, or output those private identity fields.

                When the profile includes `spokenLanguages`, treat them as a dedicated CV section (e.g. "Languages").
                When the profile includes `skills` tagged as soft skills (Communication, Leadership, etc.),
                use them as context to enrich descriptions — do not list them in a standalone "Soft Skills" section on the CV.
                When writing cover letters or application texts, weave soft skills naturally into achievement narratives.
                Return ONLY valid JSON — no markdown fences, no commentary.
                """ + languageInstruction;

        String userPrompt = "Write " + docLabel + " based on the contact-free master career profile and job description provided.\n" +
                "Return only valid JSON matching exactly this shape:\n" +
                "{\n" +
                "  \"body\": \"<full document text>\",\n" +
                "  \"keywordCoverage\": <0-100 integer>,\n" +
                "  \"matchedKeywords\": [\"keyword\"],\n" +
                "  \"missingKeywords\": [\"keyword\"]\n" +
                "}\n\n" +
                "## Contact-Free Master Career Profile JSON\n" + (contactFreeCareerProfileJson != null ? contactFreeCareerProfileJson : "") +
                "\n\n## Job Description\n" + (jobDescription != null ? jobDescription : "(no job description provided)") +
                (customInstructions != null && !customInstructions.isBlank()
                        ? "\n\n## Additional Instructions\n" + customInstructions : "");

        return new PromptComposition(systemPrompt, userPrompt, "", "", "", "", userPrompt);
    }

    private String defaultSystemPrompt() {
        return """
                You are an expert career coach and professional writer specializing in Danish job applications.
                Your goal is to help job seekers craft compelling, authentic applications that match their unique voice.
                Always write in a professional yet personal tone. Focus on concrete achievements and relevant experience.
                The career profile context intentionally excludes the user's name, contact information, profile image,
                LinkedIn URL, GitHub URL, and website URL. Do not ask for, infer, invent, or output those private identity fields.
                """;
    }
}
