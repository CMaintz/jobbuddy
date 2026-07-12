package com.autoapplicant.port.in.job;

import java.util.UUID;

/**
 * A user reports that a job posting has been taken down. The job is hidden for
 * that user immediately; the URL is then probed server-side and the job is
 * deactivated globally only if the probe confirms it is gone.
 */
public interface ReportJobInactiveUseCase {
    void reportInactive(UUID userId, UUID jobId);
}
