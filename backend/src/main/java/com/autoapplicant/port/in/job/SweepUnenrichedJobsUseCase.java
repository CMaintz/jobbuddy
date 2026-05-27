package com.autoapplicant.port.in.job;

public interface SweepUnenrichedJobsUseCase {
    SweepResult sweep(int limit);

    record SweepResult(int total, int succeeded, int failed) {}
}
