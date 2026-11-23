package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sets the user's own deadline and interview time on an application. Both are replaced: null
 * clears a date.
 *
 * @throws IllegalArgumentException when the application does not exist or is not the user's
 */
public interface UpdateApplicationDatesUseCase {
    Application updateDates(UUID applicationId, UUID userId, LocalDate deadline, LocalDateTime interviewAt);
}
