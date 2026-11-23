package com.autoapplicant.usecase.matching;

import java.util.Collection;

/** Prepares the similarities among a set of skill labels, once per feed. */
@FunctionalInterface
interface SkillSimilaritySource {

    /** How close any two of {@code labels} are in meaning; never fails, at worst knows nothing. */
    SkillSimilarity among(Collection<String> labels);
}
