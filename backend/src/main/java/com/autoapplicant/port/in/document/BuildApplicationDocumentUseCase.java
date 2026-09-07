package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;

import java.util.UUID;

public interface BuildApplicationDocumentUseCase {
    StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                String templateId, boolean showProfileImage,
                                                DocumentTheme theme);
}
