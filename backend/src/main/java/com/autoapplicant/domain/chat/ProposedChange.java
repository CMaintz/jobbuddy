package com.autoapplicant.domain.chat;

import com.autoapplicant.domain.application.ApplicationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A change as the chat model asks for it, unchecked: ids and typed values only. The model writes
 * these, and posting text can steer it, so every field is checked against her data before it
 * becomes a {@link PlanItem}.
 */
public record ProposedChange(PlanItem.Kind kind, UUID postingId, UUID applicationId,
                             ApplicationStatus toStatus, LocalDateTime interviewAt) {

    public static ProposedChange posting(PlanItem.Kind kind, UUID postingId) {
        return new ProposedChange(kind, postingId, null, null, null);
    }

    public static ProposedChange status(UUID applicationId, ApplicationStatus toStatus) {
        return new ProposedChange(PlanItem.Kind.UPDATE_STATUS, null, applicationId, toStatus, null);
    }

    public static ProposedChange interview(UUID applicationId, LocalDateTime interviewAt) {
        return new ProposedChange(PlanItem.Kind.LOG_INTERVIEW, null, applicationId, null, interviewAt);
    }
}
