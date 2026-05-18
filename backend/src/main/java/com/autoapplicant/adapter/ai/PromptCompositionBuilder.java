package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.document.*;
import com.autoapplicant.domain.job.Job;
import org.springframework.stereotype.Service;

@Service
public class PromptCompositionBuilder {

    public PromptComposition compose(PromptTemplate template, CvVersion cv, Job job, WritingProfile writingProfile) {
        return compose(template, cv, job, null, writingProfile, null);
    }

    public PromptComposition compose(PromptTemplate template, CvVersion cv, Job job,
                                     WritingProfile writingProfile, String targetLanguage) {
        return compose(template, cv, job, null, writingProfile, targetLanguage);
    }

    public PromptComposition compose(PromptTemplate template, CvVersion cv, Job job,
                                     String rawJobDescription, WritingProfile writingProfile, String targetLanguage) {
        String legacyCvContext = cv != null ? "## CV\n" + cv.content() : "";
        return compose(template, job, rawJobDescription, writingProfile, targetLanguage, legacyCvContext);
    }

    public PromptComposition compose(PromptTemplate template, Job job,
                                     String rawJobDescription, WritingProfile writingProfile,
                                     String targetLanguage, String contactFreeCareerProfileJson) {
        String baseSystem = template.systemPrompt() != null ? template.systemPrompt() : defaultSystemPrompt();
        String systemPrompt = targetLanguage != null && !targetLanguage.isBlank()
                ? baseSystem + "\nAlways write the output in " + targetLanguage + "."
                : baseSystem;
        String cvContext = contactFreeCareerProfileJson != null && !contactFreeCareerProfileJson.isBlank()
                ? "## Contact-Free Master Career Profile JSON\n" + contactFreeCareerProfileJson
                : "";
        String jobDescText = job != null ? job.descriptionClean()
                : (rawJobDescription != null && !rawJobDescription.isBlank() ? rawJobDescription : null);
        String jobDesc = jobDescText != null ? "## Job Description\n" + jobDescText : "";
        String styleMemory = buildStyleMemory(writingProfile);
        String outputConstraints = template.outputConstraints() != null ? template.outputConstraints() : "";

        String finalPrompt = String.join("\n\n",
                template.userPrompt(),
                cvContext,
                jobDesc,
                styleMemory,
                outputConstraints
        ).trim();

        return new PromptComposition(systemPrompt, template.userPrompt(), cvContext,
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
