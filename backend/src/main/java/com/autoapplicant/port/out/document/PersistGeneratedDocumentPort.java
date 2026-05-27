package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.structured.StructuredDocument;

import java.util.UUID;

public interface PersistGeneratedDocumentPort {
    StructuredDocument save(UUID userId, UUID jobId, StructuredDocument document, String modelUsed);
}
