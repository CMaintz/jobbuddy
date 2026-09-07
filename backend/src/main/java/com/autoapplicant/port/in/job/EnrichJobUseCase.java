package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;

import java.util.concurrent.CompletableFuture;

public interface EnrichJobUseCase {
    CompletableFuture<Job> enrich(Job job);
}
