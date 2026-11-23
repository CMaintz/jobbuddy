package com.autoapplicant.domain.notification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A date the user set on an application that is coming up soon: their own application deadline,
 * or an interview.
 *
 * @param time the interview's wall-clock time as the user typed it; null for a deadline
 */
public record UpcomingDate(
        Kind kind,
        UUID applicationId,
        String jobTitle,
        String companyName,
        LocalDate date,
        LocalTime time
) {
    public enum Kind { DEADLINE, INTERVIEW }
}
