package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import com.autoapplicant.port.in.document.PersistGeneratedDocumentUseCase;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.port.out.document.PersistGeneratedDocumentPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StructuredGeneratedDocumentService implements PersistGeneratedDocumentUseCase, PersistGeneratedDocumentPort {

    private final GeneratedDocumentRepositoryPort documents;
    private final ObjectMapper objectMapper;

    public StructuredGeneratedDocumentService(GeneratedDocumentRepositoryPort documents,
                                              ObjectMapper objectMapper) {
        this.documents = documents;
        this.objectMapper = objectMapper;
    }

    @Override
    public StructuredDocument save(UUID userId, UUID jobId, StructuredDocument document, String modelUsed) {
        GeneratedDocument existing = document.generatedDocumentId() != null
                ? documents.findByIdAndUserId(document.generatedDocumentId(), userId).orElse(null)
                : null;
        if (document.generatedDocumentId() != null && existing == null) {
            throw new IllegalArgumentException("Generated document not found");
        }

        GeneratedDocument saved = documents.save(new GeneratedDocument(
                existing != null ? existing.id() : null,
                userId,
                existing != null ? existing.applicationId() : null,
                jobId != null ? jobId : existing != null ? existing.jobId() : null,
                document.documentType(),
                plainText(document),
                structuredJson(withGeneratedDocumentId(document, null)),
                document.templateId(),
                document.exportMode(),
                existing != null ? existing.promptTemplateId() : null,
                existing != null ? existing.cvVersionId() : null,
                modelUsed,
                existing != null ? existing.tokensUsed() : null,
                existing != null ? existing.createdAt() : Instant.now()));

        return withGeneratedDocumentId(document, saved.id());
    }

    @Override
    public List<GeneratedDocument> listByUserId(UUID userId) {
        return documents.findByUserId(userId);
    }

    public GeneratedDocument attachToApplication(UUID userId, UUID generatedDocumentId, UUID applicationId) {
        GeneratedDocument existing = documents.findByIdAndUserId(generatedDocumentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Generated document not found"));
        return documents.save(new GeneratedDocument(
                existing.id(),
                existing.userId(),
                applicationId,
                existing.jobId(),
                existing.documentType(),
                existing.content(),
                existing.structuredContent(),
                existing.templateId(),
                existing.exportMode(),
                existing.promptTemplateId(),
                existing.cvVersionId(),
                existing.modelUsed(),
                existing.tokensUsed(),
                existing.createdAt()));
    }

    public String plainText(StructuredDocument document) {
        if (document.bodyContent() != null && !document.bodyContent().isBlank()) {
            return document.bodyContent();
        }

        List<String> lines = new ArrayList<>();
        for (StructuredDocumentSection section : nullToEmpty(document.sections())) {
            if (section.heading() != null && !section.heading().isBlank()) lines.add(section.heading());
            if (section.body() != null && !section.body().isBlank()) lines.add(section.body());
            for (StructuredDocumentItem item : nullToEmpty(section.items())) {
                add(lines, item.title());
                add(lines, item.subtitle());
                add(lines, item.location());
                add(lines, item.dateRange());
                add(lines, item.description());
                for (String bullet : nullToEmpty(item.bullets())) {
                    if (bullet != null && !bullet.isBlank()) lines.add("- " + bullet);
                }
                if (item.technologies() != null && !item.technologies().isEmpty()) {
                    lines.add(String.join(", ", item.technologies()));
                }
            }
            lines.add("");
        }
        return String.join("\n", lines).trim();
    }

    public StructuredDocument withGeneratedDocumentId(StructuredDocument document, UUID generatedDocumentId) {
        return new StructuredDocument(
                generatedDocumentId,
                document.documentType(),
                document.exportMode(),
                document.templateId(),
                document.identity(),
                document.options(),
                document.sections(),
                document.bodyContent(),
                document.atsReport());
    }

    private String structuredJson(StructuredDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize structured document", e);
        }
    }

    private static void add(List<String> lines, String value) {
        if (value != null && !value.isBlank()) lines.add(value);
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values != null ? values : List.of();
    }
}
