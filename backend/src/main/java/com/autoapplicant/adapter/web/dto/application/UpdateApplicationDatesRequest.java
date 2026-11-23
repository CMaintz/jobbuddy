package com.autoapplicant.adapter.web.dto.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Both dates, replaced together; an omitted one is cleared. */
public record UpdateApplicationDatesRequest(
        LocalDate deadline,
        LocalDateTime interviewAt
) {}
