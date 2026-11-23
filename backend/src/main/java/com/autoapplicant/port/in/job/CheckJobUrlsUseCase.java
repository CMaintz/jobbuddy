package com.autoapplicant.port.in.job;

/** Confirming that postings are still up by probing their URLs. */
public interface CheckJobUrlsUseCase {

    /**
     * Probes the postings due for a check, sized so the whole corpus is covered within the
     * recheck window. A live URL refreshes lastSeenAt; a taken-down one deactivates the posting.
     * Runs whenever it is called: the {@code app.job.url-check.enabled} switch governs only the
     * in-process schedule.
     *
     * @param runsPerDay how often the caller runs this; the batch is sized from it, so a wrong
     *     value under- or over-probes every run
     */
    void checkDueUrls(int runsPerDay);
}
