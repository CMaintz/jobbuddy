package com.autoapplicant.domain.job;

/**
 * Where a posting is in the embedding pipeline. Tracked explicitly rather than inferred from
 * whether a {@code job_embeddings} row exists, because absence cannot tell "never started" from
 * "in flight" from "failed" — and a posting with no vector is invisible to search and matching
 * while looking perfectly healthy everywhere else.
 */
public enum EmbeddingStatus {
    /** Never tried, or tried and worth trying again. */
    PENDING,
    /** A vector exists for the current model. */
    EMBEDDED,
    /** Tried too many times without success; the worker leaves it alone now. */
    FAILED;

    public static EmbeddingStatus parse(String value) {
        if (value == null) return PENDING;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
