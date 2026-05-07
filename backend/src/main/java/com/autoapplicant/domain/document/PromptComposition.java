package com.autoapplicant.domain.document;

public record PromptComposition(
        String systemPrompt,
        String userPromptTemplate,
        String cvContext,
        String jobDescription,
        String writingStyleMemory,
        String outputConstraints,
        String resolvedFinalPrompt
) {}
