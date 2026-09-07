package com.autoapplicant.domain.document;

import com.autoapplicant.domain.job.Job;

/**
 * Captures all inputs for general template-based prompt composition.
 * Use static factory methods to construct common configurations without overload proliferation.
 */
public record PromptCompositionRequest(
        PromptTemplate template,
        Job job,
        String rawJobDescription,
        WritingProfile writingProfile,
        String targetLanguage,
        String contactFreeCareerProfileJson
) {
    public static PromptCompositionRequest of(PromptTemplate template, CvVersion cv,
                                              Job job, WritingProfile writingProfile) {
        return new PromptCompositionRequest(template, job, null, writingProfile, null,
                cv != null ? "## CV\n" + cv.content() : "");
    }

    public static PromptCompositionRequest of(PromptTemplate template, CvVersion cv,
                                              Job job, WritingProfile writingProfile,
                                              String targetLanguage) {
        return new PromptCompositionRequest(template, job, null, writingProfile, targetLanguage,
                cv != null ? "## CV\n" + cv.content() : "");
    }

    public static PromptCompositionRequest of(PromptTemplate template, Job job,
                                              String rawJobDescription, WritingProfile writingProfile,
                                              String targetLanguage, String contactFreeCareerProfileJson) {
        return new PromptCompositionRequest(template, job, rawJobDescription, writingProfile,
                targetLanguage, contactFreeCareerProfileJson);
    }
}
