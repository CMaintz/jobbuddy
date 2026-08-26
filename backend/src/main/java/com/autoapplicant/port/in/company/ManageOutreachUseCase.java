package com.autoapplicant.port.in.company;

import com.autoapplicant.domain.company.OutreachContact;
import com.autoapplicant.domain.company.OutreachStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ManageOutreachUseCase {

    /** Everything the user is tracking, follow-ups due first. */
    List<OutreachContact> list(UUID userId);

    /** Starts tracking a company, or returns the existing record when it is already tracked. */
    OutreachContact track(UUID userId, UUID companyId, String companyName, String contactName);

    /**
     * Moves a tracked outreach along. Setting {@link OutreachStatus#CONTACTED} stamps the contact
     * time and, when no follow-up date is given, schedules the default one.
     */
    OutreachContact update(UUID userId, UUID id, OutreachStatus status, String channel,
                           LocalDate followUpDue, String notes);

    void untrack(UUID userId, UUID id);
}
