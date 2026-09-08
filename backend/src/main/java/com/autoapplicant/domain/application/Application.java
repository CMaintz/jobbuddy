package com.autoapplicant.domain.application;

import java.time.Instant;
import java.util.UUID;

public record Application(
        UUID id,
        UUID userId,
        UUID jobId,
        ApplicationStatus status,
        Instant appliedAt,
        String recruiterName,
        String recruiterEmail,
        String coverLetterText,
        String applicationText,
        String recruiterMessage,
        String recruiterReply,
        UUID cvVersionId,
        UUID promptTemplateId,
        Integer matchScore,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        /** Feedback received from the company (rejection reasons, interviewer comments). */
        String outcomeFeedback,
        /** What to do differently next time — fed back into future generations. */
        String outcomeLessons
) {

    /**
     * A builder pre-populated from this application — the canonical way to update a few fields
     * (status, recruiter info, outcome) without re-listing all 19 in a {@code new Application(...)}.
     * Business logic (transition rules, appliedAt) stays in the use case; this only removes the copy.
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id).userId(userId).jobId(jobId).status(status).appliedAt(appliedAt)
                .recruiterName(recruiterName).recruiterEmail(recruiterEmail)
                .coverLetterText(coverLetterText).applicationText(applicationText)
                .recruiterMessage(recruiterMessage).recruiterReply(recruiterReply)
                .cvVersionId(cvVersionId).promptTemplateId(promptTemplateId).matchScore(matchScore)
                .notes(notes).createdAt(createdAt).updatedAt(updatedAt)
                .outcomeFeedback(outcomeFeedback).outcomeLessons(outcomeLessons);
    }

    public static final class Builder {
        private UUID id;
        private UUID userId;
        private UUID jobId;
        private ApplicationStatus status;
        private Instant appliedAt;
        private String recruiterName;
        private String recruiterEmail;
        private String coverLetterText;
        private String applicationText;
        private String recruiterMessage;
        private String recruiterReply;
        private UUID cvVersionId;
        private UUID promptTemplateId;
        private Integer matchScore;
        private String notes;
        private Instant createdAt;
        private Instant updatedAt;
        private String outcomeFeedback;
        private String outcomeLessons;

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder userId(UUID v) { this.userId = v; return this; }
        public Builder jobId(UUID v) { this.jobId = v; return this; }
        public Builder status(ApplicationStatus v) { this.status = v; return this; }
        public Builder appliedAt(Instant v) { this.appliedAt = v; return this; }
        public Builder recruiterName(String v) { this.recruiterName = v; return this; }
        public Builder recruiterEmail(String v) { this.recruiterEmail = v; return this; }
        public Builder coverLetterText(String v) { this.coverLetterText = v; return this; }
        public Builder applicationText(String v) { this.applicationText = v; return this; }
        public Builder recruiterMessage(String v) { this.recruiterMessage = v; return this; }
        public Builder recruiterReply(String v) { this.recruiterReply = v; return this; }
        public Builder cvVersionId(UUID v) { this.cvVersionId = v; return this; }
        public Builder promptTemplateId(UUID v) { this.promptTemplateId = v; return this; }
        public Builder matchScore(Integer v) { this.matchScore = v; return this; }
        public Builder notes(String v) { this.notes = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }
        public Builder outcomeFeedback(String v) { this.outcomeFeedback = v; return this; }
        public Builder outcomeLessons(String v) { this.outcomeLessons = v; return this; }

        public Application build() {
            return new Application(id, userId, jobId, status, appliedAt, recruiterName, recruiterEmail,
                    coverLetterText, applicationText, recruiterMessage, recruiterReply, cvVersionId,
                    promptTemplateId, matchScore, notes, createdAt, updatedAt, outcomeFeedback, outcomeLessons);
        }
    }
}
