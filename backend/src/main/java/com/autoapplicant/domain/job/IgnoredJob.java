package com.autoapplicant.domain.job;

import java.time.Instant;
import java.util.UUID;

public record IgnoredJob(UUID id, UUID userId, UUID jobId, String reason, Instant ignoredAt) {}
