package com.autoapplicant.domain.analytics;

import java.time.Instant;
import java.util.UUID;

public record ResponseMetric(
    UUID id,
    UUID userId,
    UUID jobId,
    UUID applicationId,
    String eventType,
    Instant eventAt,
    String notes
) {}
