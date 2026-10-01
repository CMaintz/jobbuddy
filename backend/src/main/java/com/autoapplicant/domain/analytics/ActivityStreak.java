package com.autoapplicant.domain.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consecutive calendar days, in the user's own time zone, with at least one bit of job-search
 * activity. A day with nothing yet doesn't break the run until it's over, so a streak that
 * ended yesterday still counts as ongoing today.
 */
public record ActivityStreak(int days) {

    public static ActivityStreak of(Collection<Instant> activity, ZoneId zone, LocalDate today) {
        Set<LocalDate> activeDays = activity.stream()
                .map(at -> LocalDate.ofInstant(at, zone))
                .collect(Collectors.toSet());
        LocalDate day = activeDays.contains(today) ? today : today.minusDays(1);
        int days = 0;
        while (activeDays.contains(day)) {
            days++;
            day = day.minusDays(1);
        }
        return new ActivityStreak(days);
    }
}
