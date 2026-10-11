package com.autoapplicant.port.in.job;

import java.time.Duration;

public interface SweepUnenrichedJobsUseCase {

    SweepResult sweep(int limit);

    /** As {@link #sweep(int)}, but stops early once {@code budget} has elapsed. */
    SweepResult sweep(int limit, Duration budget);

    /**
     * @param gaveUp how many hit the attempt limit on this pass and will not be tried again
     */
    record SweepResult(int total, int succeeded, int failed, int gaveUp) {}
}
