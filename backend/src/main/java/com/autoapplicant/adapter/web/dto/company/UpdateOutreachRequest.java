package com.autoapplicant.adapter.web.dto.company;

import com.autoapplicant.domain.company.OutreachStatus;

import java.time.LocalDate;

/**
 * Move a tracked outreach along. Every field is optional — a null leaves that part unchanged,
 * so the UI can send just the status change.
 */
public record UpdateOutreachRequest(OutreachStatus status, String channel,
                                    LocalDate followUpDue, String notes) {}
