package com.autoapplicant.domain.analytics;

import java.util.List;

public record DetailedMetrics(
        int total,
        int saved,
        int applied,
        int pendingResponse,
        int activeInterviews,
        int offers,
        int appliedThisWeek,
        int appliedThisMonth,
        double responseRate,
        double interviewRate,
        double offerRate,
        List<String> topCompanies
) {}
