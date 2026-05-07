package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.GeneratedDocument;

import java.util.List;
import java.util.UUID;

public interface GeneratedDocumentRepositoryPort {
    GeneratedDocument save(GeneratedDocument document);
    List<GeneratedDocument> findByApplicationId(UUID applicationId);
    List<GeneratedDocument> findByUserId(UUID userId);
}
