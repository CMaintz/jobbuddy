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
        String baseSystem = template.systemPrompt() != null ? template.systemPrompt() : defaultSystemPrompt();
        String systemPrompt = targetLanguage != null && !targetLanguage.isBlank()
                ? baseSystem + "\nAlways write the output in " + targetLanguage + "."
                : baseSystem;
        String cvContext = cv != null ? "## CV\n" + cv.content() : "";
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

    private String defaultSystemPrompt() {
        return """
                You are an expert career coach and professional writer specializing in Danish job applications.
                Your goal is to help job seekers craft compelling, authentic applications that match their unique voice.
                Always write in a professional yet personal tone. Focus on concrete achievements and relevant experience.
                """;
    }
}
