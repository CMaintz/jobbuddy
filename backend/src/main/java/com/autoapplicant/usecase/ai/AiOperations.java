package com.autoapplicant.usecase.ai;

/**
 * The labels the AI usage log files generations under. Keeping them in one place
 * stops the same operation being spelled two ways and splitting its own totals.
 * Each must fit the {@code operation} column (varchar 50).
 */
public final class AiOperations {

    public static final String CV_ANALYSIS = "CV_ANALYSIS";
    public static final String CV_PARSE = "CV_PARSE";
    public static final String LINKEDIN_PARSE = "LINKEDIN_PARSE";
    public static final String DOCUMENT_GENERATION = "DOCUMENT_GENERATION";
    public static final String DOCUMENT_REFINE = "DOCUMENT_REFINE";
    public static final String DOCUMENT_REVIEW = "DOCUMENT_REVIEW";
    public static final String TAILORED_CV = "TAILORED_CV";
    public static final String TAILORED_CV_REVIEW = "TAILORED_CV_REVIEW";
    public static final String INTERVIEW_QUESTIONS = "INTERVIEW_QUESTIONS";
    public static final String INTERVIEW_PREP_PACK = "INTERVIEW_PREP_PACK";
    public static final String MOCK_INTERVIEW = "MOCK_INTERVIEW";
    public static final String COMPANY_GROUNDING = "COMPANY_GROUNDING";
    public static final String SKILL_GAP = "SKILL_GAP";
    public static final String SKILL_EVIDENCE = "SKILL_EVIDENCE";
    public static final String WRITING_PROFILE = "WRITING_PROFILE";

    private AiOperations() {}
}
