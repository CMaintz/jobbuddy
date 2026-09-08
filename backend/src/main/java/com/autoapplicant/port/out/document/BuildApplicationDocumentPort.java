package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.JobKeywords;
import com.autoapplicant.domain.document.structured.StructuredDocument;

import java.util.UUID;

public interface BuildApplicationDocumentPort {
    StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                String templateId,
                                                JobKeywords jobKeywords,
                                                boolean showProfileImage,
                                                DocumentTheme theme,
                                                ContentGuardFindings guardFindings);
}
