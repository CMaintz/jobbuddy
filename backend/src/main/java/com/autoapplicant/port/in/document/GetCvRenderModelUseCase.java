package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;

import java.util.UUID;

public interface GetCvRenderModelUseCase {
    StructuredDocument buildCv(UUID userId, String templateId, boolean showProfileImage, DocumentTheme theme);
}
