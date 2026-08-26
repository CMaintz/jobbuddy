package com.autoapplicant.domain.skill;

/**
 * A skill the candidate claims, the market asks for, and nothing in the profile can prove.
 *
 * <p>This is the gap the Danish market rules care about most: an unproven competence is the
 * most-cited rejection reason, and {@code DocumentQualityEvaluator}'s evidence dimension measures
 * exactly the shortfall it causes in a generated letter.
 *
 * <p>Derived on demand rather than queued: a gap closes the moment a story is written, and a
 * stored to-do list would go stale the first time the user edited their profile elsewhere.
 *
 * @param marketFrequency how many of the user's matched postings ask for it — why this gap first
 * @param question        a concrete opening question, phrased for the skill
 */
public record EvidenceGap(String skillName, int marketFrequency, String question) {}
