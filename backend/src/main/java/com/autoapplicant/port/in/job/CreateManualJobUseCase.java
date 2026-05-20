package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;

public interface CreateManualJobUseCase {
    Job createManualJob(Job job);
}
