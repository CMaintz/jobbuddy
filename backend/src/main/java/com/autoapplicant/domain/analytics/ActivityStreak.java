package com.autoapplicant.domain.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Consecutive calendar days, in the user's own time zone, with at least one bit of job-search
 * activity. A day with nothing yet doesn't break the run until it's over, so a streak that
 * ended yesterday still counts as ongoing today. {@code lastWeek} is the activity count per
 * day for the seven days ending today, oldest first.
 */
public record ActivityStreak(int days, List<Integer> lastWeek) {

    static final int WEEK = 7;

    public static ActivityStreak of(Collection<Instant> activity, ZoneId zone, LocalDate today) {
        Map<LocalDate, Long> perDay = activity.stream()
                .collect(Collectors.groupingBy(at -> LocalDate.ofInstant(at, zone), Collectors.counting()));
        return new ActivityStreak(runEndingAt(perDay, today), lastWeek(perDay, today));
    }

    private static int runEndingAt(Map<LocalDate, Long> perDay, LocalDate today) {
        LocalDate day = perDay.containsKey(today) ? today : today.minusDays(1);
        int days = 0;
        while (perDay.containsKey(day)) {
            days++;
            day = day.minusDays(1);
        }
        return days;
    }

    private static List<Integer> lastWeek(Map<LocalDate, Long> perDay, LocalDate today) {
        Function<LocalDate, Integer> count = day -> perDay.getOrDefault(day, 0L).intValue();
        return IntStream.range(0, WEEK)
                .mapToObj(i -> today.minusDays(WEEK - 1L - i))
                .map(count)
                .toList();
    }
}
