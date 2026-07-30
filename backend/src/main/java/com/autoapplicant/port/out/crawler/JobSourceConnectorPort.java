package com.autoapplicant.port.out.crawler;

import com.autoapplicant.domain.job.JobSource;
public interface JobSourceConnectorPort {
    JobSource getSource();
    void fetchJobs(CrawlConfig config);

    /**
     * Whether this connector participates in the bulk "run all sources" operations
     * (the scheduled fan-out and the CLI backfills). Sources that must run on their
     * own controlled, low-volume cadence (e.g. LinkedIn) override this to {@code false};
     * they are then only ever invoked by an explicit per-source trigger.
     */
    default boolean includeInDefaultSchedule() {
        return true;
    }
}
