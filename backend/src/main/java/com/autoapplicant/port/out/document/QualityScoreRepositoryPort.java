package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.RecordedQualityScore;

import java.util.List;
import java.util.UUID;

public interface QualityScoreRepositoryPort {

    RecordedQualityScore save(RecordedQualityScore score);

    /** The user's most recent scores, newest first — the trend behind the number. */
    List<RecordedQualityScore> findRecent(UUID userId, int limit);
}
