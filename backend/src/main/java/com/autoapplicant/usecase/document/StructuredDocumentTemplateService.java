package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.StructuredDocumentTemplate;
import com.autoapplicant.port.in.document.GetStructuredDocumentTemplatesUseCase;
import com.autoapplicant.port.out.document.StructuredDocumentTemplateRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StructuredDocumentTemplateService implements GetStructuredDocumentTemplatesUseCase {

    private final StructuredDocumentTemplateRepositoryPort templates;

    public StructuredDocumentTemplateService(StructuredDocumentTemplateRepositoryPort templates) {
        this.templates = templates;
    }

    @Override
    public List<StructuredDocumentTemplate> getActiveTemplates() {
        return templates.findActive();
    }
}
