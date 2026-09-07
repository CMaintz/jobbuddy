package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.StructuredDocumentTemplate;

import java.util.List;

public interface StructuredDocumentTemplateRepositoryPort {
    List<StructuredDocumentTemplate> findActive();
}
