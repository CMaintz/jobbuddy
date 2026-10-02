package com.autoapplicant.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityStreakTest {

    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Test
    void no_activity_is_no_streak() {
        ActivityStreak none = ActivityStreak.of(List.of(), CPH, TODAY);

        assertThat(none.days()).isZero();
        assertThat(none.lastWeek()).containsExactly(0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void activity_only_today_is_one_day() {
        assertThat(streak(at(TODAY, 9))).isEqualTo(1);
    }

    @Test
    void several_events_on_one_day_count_once() {
        assertThat(streak(at(TODAY, 9), at(TODAY, 12), at(TODAY, 18))).isEqualTo(1);
    }

    @Test
    void counts_consecutive_days_up_to_today() {
        assertThat(streak(at(TODAY, 9), at(TODAY.minusDays(1), 9), at(TODAY.minusDays(2), 9))).isEqualTo(3);
    }

    @Test
    void quiet_today_keeps_yesterdays_streak_going() {
        assertThat(streak(at(TODAY.minusDays(1), 20), at(TODAY.minusDays(2), 8))).isEqualTo(2);
    }

    @Test
    void streak_that_ended_two_days_ago_is_over() {
        assertThat(streak(at(TODAY.minusDays(2), 9), at(TODAY.minusDays(3), 9))).isZero();
    }

    @Test
    void a_gap_ends_the_streak() {
        assertThat(streak(at(TODAY, 9), at(TODAY.minusDays(1), 9), at(TODAY.minusDays(3), 9), at(TODAY.minusDays(4), 9)))
                .isEqualTo(2);
    }

    @Test
    void future_activity_is_ignored() {
        assertThat(streak(at(TODAY.plusDays(1), 9))).isZero();
    }

    @Test
    void days_follow_the_users_zone_not_utc() {
        // 22:30 UTC on 30 Sep is already 1 Oct in Copenhagen, but still 30 Sep in New York.
        Instant lateEvening = Instant.parse("2026-09-30T22:30:00Z");

        assertThat(ActivityStreak.of(List.of(lateEvening), CPH, TODAY).days()).isEqualTo(1);
        assertThat(ActivityStreak.of(List.of(lateEvening), ZoneId.of("America/New_York"), TODAY).days())
                .isEqualTo(1);
        assertThat(ActivityStreak.of(List.of(lateEvening), ZoneId.of("America/New_York"), TODAY.plusDays(1)).days())
                .isZero();
    }

    @Test
    void just_before_and_after_local_midnight_are_two_days() {
        Instant beforeMidnight = LocalDateTime.of(2026, 9, 30, 23, 59).atZone(CPH).toInstant();
        Instant afterMidnight = LocalDateTime.of(2026, 10, 1, 0, 1).atZone(CPH).toInstant();

        assertThat(ActivityStreak.of(List.of(beforeMidnight, afterMidnight), CPH, TODAY).days()).isEqualTo(2);
    }

    @Test
    void survives_a_daylight_saving_change() {
        // Copenhagen springs forward on 29 Mar 2026, so that day is only 23 hours long.
        LocalDate dstDay = LocalDate.of(2026, 3, 29);
        Instant before = LocalDateTime.of(2026, 3, 28, 23, 30).atZone(CPH).toInstant();
        Instant during = LocalDateTime.of(2026, 3, 29, 0, 30).atZone(CPH).toInstant();
        Instant after = LocalDateTime.of(2026, 3, 30, 0, 10).atZone(CPH).toInstant();

        assertThat(ActivityStreak.of(List.of(before, during, after), CPH, dstDay.plusDays(1)).days()).isEqualTo(3);
    }

    @Test
    void counts_the_last_seven_days_oldest_first() {
        ActivityStreak streak = ActivityStreak.of(
                List.of(at(TODAY, 9), at(TODAY, 15), at(TODAY.minusDays(2), 9), at(TODAY.minusDays(6), 9),
                        at(TODAY.minusDays(7), 9)),
                CPH, TODAY);

        assertThat(streak.lastWeek()).containsExactly(1, 0, 0, 0, 1, 0, 2);
    }

    private static int streak(Instant... activity) {
        return ActivityStreak.of(List.of(activity), CPH, TODAY).days();
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(CPH).toInstant();
    }
}
