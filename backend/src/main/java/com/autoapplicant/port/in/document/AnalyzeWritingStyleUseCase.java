package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.WritingProfile;

import java.util.List;
import java.util.UUID;

public interface AnalyzeWritingStyleUseCase {

    /**
     * Derives a proposed writing-style profile from texts the user wrote —
     * pasted samples and/or their own generated documents (valuable once the
     * user has edited those into their own voice). The result is a proposal
     * only: nothing is persisted until the user saves it via
     * {@link ManageWritingProfileUseCase#save}.
     */
    WritingProfile analyze(UUID userId, List<String> samples, List<UUID> documentIds);
}
