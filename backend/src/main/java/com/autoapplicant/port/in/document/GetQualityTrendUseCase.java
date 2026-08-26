package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.RecordedQualityScore;

import java.util.List;
import java.util.UUID;

public interface GetQualityTrendUseCase {

    /** The user's recent document quality scores, newest first. */
    List<RecordedQualityScore> recentScores(UUID userId, int limit);
}
