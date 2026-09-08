package com.autoapplicant.domain.job;

/**
 * Where a posting is in the enrichment pipeline. Tracked explicitly rather than
 * inferred from whether a summary happens to be present, so a job that fails
 * repeatedly can be given up on instead of retried forever.
 */
public enum EnrichmentStatus {
    /** Never tried, or tried and worth trying again. */
    PENDING,
    /** Enrichment produced a usable result. */
    ENRICHED,
    /** Tried too many times without success; the sweep leaves it alone now. */
    FAILED;

    public static EnrichmentStatus parse(String value) {
        if (value == null) return PENDING;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
