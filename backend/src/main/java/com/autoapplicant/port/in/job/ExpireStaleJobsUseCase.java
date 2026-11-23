package com.autoapplicant.port.in.job;

/** Retiring postings that are past their deadline or have not been seen for too long. */
public interface ExpireStaleJobsUseCase {

    /**
     * Deactivates postings whose application deadline has passed and postings not seen by a
     * crawl or a URL probe within the stale window. Run it after the probe, which is what keeps
     * live postings that connectors no longer revisit from looking stale.
     */
    void deactivateStaleJobs();
}
