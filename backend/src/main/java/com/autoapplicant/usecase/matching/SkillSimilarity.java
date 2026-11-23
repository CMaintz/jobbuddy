package com.autoapplicant.usecase.matching;

/** How close two skill labels are in meaning, as the cosine of their embeddings. */
@FunctionalInterface
interface SkillSimilarity {

    /** No embeddings to compare, as when the provider is down: only spelling relates skills. */
    SkillSimilarity NONE = (ask, skill) -> 0;

    /** The cosine of the two labels' embeddings; 0 when either label has none. */
    double between(String ask, String skill);
}
