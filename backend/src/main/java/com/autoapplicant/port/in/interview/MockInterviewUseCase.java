package com.autoapplicant.port.in.interview;

import com.autoapplicant.domain.interview.MockInterviewTurn;

import java.util.List;
import java.util.UUID;

public interface MockInterviewUseCase {
    /**
     * Continues a stateless mock interview for this job. The full transcript is
     * supplied each turn; nothing is persisted server-side. An empty transcript
     * yields the interviewer's opening. When {@code wrapUp} is true the AI drops
     * the interviewer persona and returns coaching feedback on the whole session.
     */
    String respond(UUID userId, UUID jobId, List<MockInterviewTurn> transcript, boolean wrapUp);
}
