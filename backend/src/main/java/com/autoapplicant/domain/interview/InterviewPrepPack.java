package com.autoapplicant.domain.interview;

import java.util.List;

/**
 * One-shot interview preparation for a job: gap-targeted questions (persisted),
 * plus a consistency brief and questions to ask (transient).
 */
public record InterviewPrepPack(
        /** Likely questions, targeted at posting requirements and profile gaps. Persisted. */
        List<InterviewQuestion> questions,
        /** Claims made in the generated CV/letters the candidate must be ready to defend. */
        List<String> consistencyBrief,
        /** Good questions for the candidate to ask the interviewer. */
        List<String> questionsToAsk
) {}
