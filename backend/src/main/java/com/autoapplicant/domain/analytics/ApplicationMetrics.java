package com.autoapplicant.domain.analytics;

import java.time.LocalDate;
import java.util.UUID;

public record ApplicationMetrics(
        UUID id,
        UUID userId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalApplications,
        int totalSaved,
        int totalIgnored,
        double responseRate,
        double interviewRate,
        double offerRate
) {}
