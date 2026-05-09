package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PdfTemplate;
import com.autoapplicant.port.in.document.ManagePdfTemplatesUseCase;
import com.autoapplicant.port.out.document.PdfTemplateRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PdfTemplateService implements ManagePdfTemplatesUseCase {

    private final PdfTemplateRepositoryPort repo;

    public PdfTemplateService(PdfTemplateRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<PdfTemplate> getTemplates(UUID userId) {
        return repo.findAvailable(userId);
    }

    @Override
    public Optional<PdfTemplate> getById(UUID id) {
        return repo.findById(id);
    }

    @Override
    public PdfTemplate create(PdfTemplate template) {
        PdfTemplate toSave = new PdfTemplate(null, template.userId(), template.name(),
                template.description(), template.documentType(), template.htmlTemplate(),
                template.cssStyles(), false, true, null);
        return repo.save(toSave);
    }

    @Override
    public PdfTemplate update(PdfTemplate template) {
        return repo.findById(template.id())
                .filter(existing -> !existing.isSystem())
                .map(existing -> repo.save(template))
                .orElseThrow(() -> new IllegalArgumentException("Template not found or is a system template"));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        repo.findById(id)
                .filter(t -> !t.isSystem() && userId.equals(t.userId()))
                .ifPresent(t -> repo.deleteById(id));
    }
}
