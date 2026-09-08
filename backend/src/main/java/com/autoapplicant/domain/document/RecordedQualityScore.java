package com.autoapplicant.domain.document;

import java.time.Instant;
import java.util.UUID;

/**
 * A quality score as it was recorded for one generated document.
 *
 * <p>Kept as history rather than a column on the document: the rubric will change, and a score
 * only means something next to the moment it was taken.
 */
public record RecordedQualityScore(
        UUID id,
        UUID userId,
        UUID generatedDocumentId,
        String documentType,
        QualityScore score,
        Instant createdAt) {}
