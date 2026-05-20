package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.StructuredDocumentTemplate;

import java.util.List;

public interface GetStructuredDocumentTemplatesUseCase {
    List<StructuredDocumentTemplate> getActiveTemplates();
}
