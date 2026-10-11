package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Saves a structured document as a generated-document row: its plain text for search and
 * export, and its structure as JSON. Shared by the save endpoint and the generation pipeline,
 * so it is a helper rather than a use case of its own.
 */
@Component
public class GeneratedDocumentWriter {

    private final GeneratedDocumentRepositoryPort documents;
    private final ObjectMapper objectMapper;

    public GeneratedDocumentWriter(GeneratedDocumentRepositoryPort documents,
                                   ObjectMapper objectMapper) {
        this.documents = documents;
        this.objectMapper = objectMapper;
    }

    public StructuredDocument save(UUID userId, UUID jobId, StructuredDocument document, String modelUsed) {
        GeneratedDocument row = document.generatedDocumentId() != null
                ? revised(findOwned(userId, document.generatedDocumentId()), jobId, document, modelUsed)
                : created(userId, jobId, document, modelUsed);
        return withGeneratedDocumentId(document, documents.save(row).id());
    }

    private GeneratedDocument findOwned(UUID userId, UUID generatedDocumentId) {
        return documents.findByIdAndUserId(generatedDocumentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Generated document not found"));
    }

    /** A re-save replaces the content and keeps the row's links and history. */
    private GeneratedDocument revised(GeneratedDocument existing, UUID jobId, StructuredDocument document,
                                      String modelUsed) {
        return new GeneratedDocument(
                existing.id(),
                existing.userId(),
                existing.applicationId(),
                jobId != null ? jobId : existing.jobId(),
                document.documentType(),
                plainText(document),
                structuredJson(withGeneratedDocumentId(document, null)),
                document.templateId(),
                document.exportMode(),
                existing.promptTemplateId(),
                existing.cvVersionId(),
                modelUsed,
                existing.tokensUsed(),
                existing.createdAt());
    }

    private GeneratedDocument created(UUID userId, UUID jobId, StructuredDocument document, String modelUsed) {
        return new GeneratedDocument(
                null,
                userId,
                null,
                jobId,
                document.documentType(),
                plainText(document),
                structuredJson(withGeneratedDocumentId(document, null)),
                document.templateId(),
                document.exportMode(),
                null,
                null,
                modelUsed,
                null,
                Instant.now());
    }

    private static String plainText(StructuredDocument document) {
        if (document.bodyContent() != null && !document.bodyContent().isBlank()) {
            return document.bodyContent();
        }
        List<String> lines = new ArrayList<>();
        for (StructuredDocumentSection section : nullToEmpty(document.sections())) {
            addSection(lines, section);
        }
        return String.join("\n", lines).trim();
    }

    private static void addSection(List<String> lines, StructuredDocumentSection section) {
        add(lines, section.heading());
        add(lines, section.body());
        for (StructuredDocumentItem item : nullToEmpty(section.items())) {
            addItem(lines, item);
        }
        lines.add("");
    }

    private static void addItem(List<String> lines, StructuredDocumentItem item) {
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

    private static StructuredDocument withGeneratedDocumentId(StructuredDocument document, UUID generatedDocumentId) {
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
