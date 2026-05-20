package com.autoapplicant.adapter.web.dto.pdf;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.adapter.web.dto.DocumentThemeRequest;

public record StructuredApplicationRequest(
        DocumentType documentType,
        String content,
        String templateId,
        Boolean showProfileImage,
        DocumentThemeRequest theme
) {}
