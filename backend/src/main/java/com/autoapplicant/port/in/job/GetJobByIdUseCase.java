package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;

import java.util.Optional;
import java.util.UUID;

public interface GetJobByIdUseCase {
    Optional<Job> getJobById(UUID id);
    Optional<Job> lookupByUrl(String url);
}
