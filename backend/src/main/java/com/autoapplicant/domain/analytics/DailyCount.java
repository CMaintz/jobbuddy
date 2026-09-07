package com.autoapplicant.domain.analytics;

import java.time.LocalDate;

public record DailyCount(LocalDate date, int count) {}
