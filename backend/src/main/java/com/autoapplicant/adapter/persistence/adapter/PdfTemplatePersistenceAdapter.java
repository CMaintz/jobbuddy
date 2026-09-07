package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.PdfTemplateEntity;
import com.autoapplicant.adapter.persistence.repository.PdfTemplateJpaRepository;
import com.autoapplicant.domain.document.PdfTemplate;
import com.autoapplicant.port.out.document.PdfTemplateRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PdfTemplatePersistenceAdapter implements PdfTemplateRepositoryPort {

    private final PdfTemplateJpaRepository repo;

    public PdfTemplatePersistenceAdapter(PdfTemplateJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<PdfTemplate> findAvailable(UUID userId) {
        return repo.findAvailableForUser(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PdfTemplate> findById(UUID id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Override
    public PdfTemplate save(PdfTemplate template) {
        return toDomain(repo.save(toEntity(template)));
    }

    @Override
    public void deleteById(UUID id) {
        repo.deleteById(id);
    }

    private PdfTemplate toDomain(PdfTemplateEntity e) {
        return new PdfTemplate(e.getId(), e.getUserId(), e.getName(), e.getDescription(),
                e.getDocumentType(), e.getHtmlTemplate(), e.getCssStyles(),
                e.isSystem(), e.isActive(), e.getCreatedAt());
    }

    private PdfTemplateEntity toEntity(PdfTemplate t) {
        PdfTemplateEntity e = new PdfTemplateEntity();
        e.setId(t.id());
        e.setUserId(t.userId());
        e.setName(t.name());
        e.setDescription(t.description());
        e.setDocumentType(t.documentType());
        e.setHtmlTemplate(t.htmlTemplate());
        e.setCssStyles(t.cssStyles());
        e.setSystem(t.isSystem());
        e.setActive(t.isActive());
        return e;
    }
}
