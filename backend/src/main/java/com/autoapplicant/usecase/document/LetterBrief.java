package com.autoapplicant.usecase.document;

/**
 * What a letter is judged and rewritten against: its type, the posting, and the language and market
 * it targets. Deliberately without the profile or the needs, which the gate must not see.
 */
public record LetterBrief(String documentType, String posting, String targetLanguage, String jobCountry) {

    GenerationGuardrails guardrails() {
        return GenerationGuardrails.forDocument(documentType, targetLanguage, posting, jobCountry);
    }

    String postingOr(String fallback) {
        return posting != null && !posting.isBlank() ? posting : fallback;
    }
}
