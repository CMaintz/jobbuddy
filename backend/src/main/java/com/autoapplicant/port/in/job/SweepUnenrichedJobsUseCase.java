package com.autoapplicant.port.in.job;

public interface SweepUnenrichedJobsUseCase {

    SweepResult sweep(int limit);

    /**
     * @param gaveUp how many hit the attempt limit on this pass and will not be tried again
     */
    record SweepResult(int total, int succeeded, int failed, int gaveUp) {}
}
