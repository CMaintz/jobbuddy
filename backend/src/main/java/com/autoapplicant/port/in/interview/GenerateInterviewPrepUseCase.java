package com.autoapplicant.port.in.interview;

import com.autoapplicant.domain.interview.InterviewPrepPack;

import java.util.UUID;

public interface GenerateInterviewPrepUseCase {
    /**
     * Builds a prep pack for the user's application to this job: gap-targeted
     * questions (persisted alongside existing ones), a consistency brief drawn
     * from the documents actually sent, and questions to ask the interviewer.
     */
    InterviewPrepPack generatePrepPack(UUID userId, UUID jobId);
}
