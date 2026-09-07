package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.StructuredDocument;

import java.util.List;
import java.util.UUID;

public interface PersistGeneratedDocumentUseCase {
    StructuredDocument save(UUID userId, UUID jobId, StructuredDocument document, String modelUsed);
    List<GeneratedDocument> listByUserId(UUID userId);
}
