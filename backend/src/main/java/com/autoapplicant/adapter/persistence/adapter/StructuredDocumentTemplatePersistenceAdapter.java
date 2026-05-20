package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.StructuredDocumentTemplateEntity;
import com.autoapplicant.adapter.persistence.repository.StructuredDocumentTemplateJpaRepository;
import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.StructuredDocumentTemplate;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.port.out.document.StructuredDocumentTemplateRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StructuredDocumentTemplatePersistenceAdapter implements StructuredDocumentTemplateRepositoryPort {

    private final StructuredDocumentTemplateJpaRepository repo;

    public StructuredDocumentTemplatePersistenceAdapter(StructuredDocumentTemplateJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<StructuredDocumentTemplate> findActive() {
        Map<String, List<StructuredDocumentTemplateEntity>> rowsByTemplate = new LinkedHashMap<>();
        for (StructuredDocumentTemplateEntity row : repo.findByActiveTrueOrderByDisplayOrderAscNameAsc()) {
            rowsByTemplate.computeIfAbsent(row.getTemplateId(), ignored -> new ArrayList<>()).add(row);
        }
        return rowsByTemplate.values().stream()
                .map(this::toDomain)
                .sorted(Comparator.comparingInt(StructuredDocumentTemplate::displayOrder))
                .toList();
    }

    private StructuredDocumentTemplate toDomain(List<StructuredDocumentTemplateEntity> rows) {
        StructuredDocumentTemplateEntity first = rows.get(0);
        List<DocumentType> documentTypes = rows.stream()
                .map(StructuredDocumentTemplateEntity::getDocumentType)
                .map(DocumentType::valueOf)
                .distinct()
                .toList();
        return new StructuredDocumentTemplate(
                first.getTemplateId(),
                first.getFamilyId(),
                first.getFamilyName(),
                displayName(first, documentTypes),
                first.getDescription(),
                documentTypes,
                first.getLayoutType(),
                first.getExportMode(),
                rows.stream().anyMatch(StructuredDocumentTemplateEntity::isSupportsProfileImage),
                rows.stream().allMatch(StructuredDocumentTemplateEntity::isAtsSafe),
                rows.stream().mapToInt(StructuredDocumentTemplateEntity::getDisplayOrder).min().orElse(first.getDisplayOrder()),
                new DocumentTheme(
                        first.getDefaultPrimaryColor(),
                        first.getDefaultAccentColor(),
                        first.getDefaultFontFamily(),
                        first.getDefaultFontScale()));
    }

    private String displayName(StructuredDocumentTemplateEntity first, List<DocumentType> documentTypes) {
        if (documentTypes.size() > 1 && !documentTypes.contains(DocumentType.CV)) {
            return first.getFamilyName() + " Application";
        }
        return first.getName();
    }
}
