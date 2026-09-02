package com.autoapplicant.domain.matching;

/**
 * What the user told us about a recommendation.
 *
 * <p>Two axes, deliberately. LIKE and DISLIKE are about <em>this posting</em> and move only its
 * own score. MORE_LIKE_THIS and FEWER_LIKE_THIS are about the <em>kind</em> of posting and steer
 * the ones near it in embedding space — which is the whole difference between them, and the half
 * that was never built: for a long time all four were treated identically, so "more like this"
 * only ever raised the score of a job the user had already seen and judged.
 *
 * <p>There is no HIDE. Ignoring a job already means "take it out of my feed", it records a reason,
 * and it can be undone from a list the user can see. A second mechanism for the same act was what
 * made the two impossible to tell apart.
 */
public enum FeedbackType {
    /** This posting is good. Raises this posting only. */
    LIKE,
    /** This posting is not for me. Lowers this posting only. */
    DISLIKE,
    /** Show me more postings like this one. Raises its neighbours. */
    MORE_LIKE_THIS,
    /** Show me fewer postings like this one. Lowers its neighbours. */
    FEWER_LIKE_THIS
}
