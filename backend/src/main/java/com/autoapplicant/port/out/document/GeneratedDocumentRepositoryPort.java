package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedDocumentRepositoryPort {
    GeneratedDocument save(GeneratedDocument document);
    List<GeneratedDocument> findByApplicationId(UUID applicationId);
    List<GeneratedDocument> findByUserId(UUID userId);
    List<GeneratedDocument> findByJobIdAndUserId(UUID jobId, UUID userId);
    Optional<GeneratedDocument> findByIdAndUserId(UUID id, UUID userId);
    List<GeneratedDocument> findRecentByUserIdAndType(UUID userId, String documentType, int limit);

    /**
     * Sets the user's document's structured content to {@code replacement}, touching no other
     * column, but only while it still equals {@code expected}.
     *
     * @return false when no row matched: the document is gone, belongs to someone else, or its
     *     structured content is no longer {@code expected}
     */
    boolean replaceStructuredContent(UUID id, UUID userId, String expected, String replacement);
}
