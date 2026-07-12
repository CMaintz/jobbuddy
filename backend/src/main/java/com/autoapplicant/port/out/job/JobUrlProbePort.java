package com.autoapplicant.port.out.job;

import com.autoapplicant.domain.job.UrlProbeOutcome;

/** Probes a job posting URL to determine whether the posting is still live. */
public interface JobUrlProbePort {
    UrlProbeOutcome probe(String url);
}
