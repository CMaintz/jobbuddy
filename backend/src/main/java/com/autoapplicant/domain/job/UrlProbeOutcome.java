package com.autoapplicant.domain.job;

/** Result of probing a job posting URL to see whether the posting is still up. */
public enum UrlProbeOutcome {
    /** Page loaded and shows no signs of the posting being closed. */
    ALIVE,
    /** Definitive: HTTP 404/410 — the posting URL no longer exists. */
    GONE,
    /** Page loads but contains "no longer available" style markers (soft 404). */
    GONE_SOFT,
    /** Couldn't tell: network error, bot protection (403/999), or server error. */
    INCONCLUSIVE
}
