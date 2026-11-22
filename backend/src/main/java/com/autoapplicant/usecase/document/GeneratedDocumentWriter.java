package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.LetterReview;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
        if (document.generatedDocumentId() == null) {
            StructuredDocument kept = document.withLetterReview(checked(document.letterReview(), document));
            return withGeneratedDocumentId(kept, documents.save(created(userId, jobId, kept, modelUsed)).id());
        }
        GeneratedDocument existing = findOwned(userId, document.generatedDocumentId());
        StructuredDocument kept = document.withLetterReview(storedReview(existing, document.bodyContent()));
        return withGeneratedDocumentId(kept, documents.save(revised(existing, jobId, kept, modelUsed)).id());
    }

    /**
     * Stores {@code review} on the existing row in place of the one it has. Unlike {@link #save},
     * which keeps the stored verdict, this can clear a stale one, so {@code review} must judge the
     * body in {@code document}, which was read from {@code existing}.
     *
     * <p>{@code existing} can be minutes old by now, since the gate call sits between the read and
     * this write. So only the structured content is written, and only while it is still the one
     * read: a link or other column set meanwhile stays, and a letter edited meanwhile is not given
     * a verdict on text the gate never saw.
     *
     * @throws IllegalStateException when the letter changed or went away since {@code existing}
     */
    public StructuredDocument replaceLetterReview(GeneratedDocument existing, StructuredDocument document,
                                                  LetterReview review) {
        StructuredDocument reviewed = document.withLetterReview(review);
        if (!documents.replaceStructuredContent(existing.id(), existing.userId(), existing.structuredContent(),
                structuredJson(withGeneratedDocumentId(reviewed, null)))) {
            throw new IllegalStateException("This letter changed while it was being checked");
        }
        return withGeneratedDocumentId(reviewed, existing.id());
    }

    /**
     * A new row's review, marked stale once the text differs from what the gate judged. The editor
     * saves an edit as a new row carrying the old structure, so this is where an edited letter's
     * verdict stops claiming to describe it.
     */
    private static LetterReview checked(LetterReview review, StructuredDocument document) {
        return review != null ? review.checkedAgainst(document.bodyContent()) : null;
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

    /**
     * The stored row's letter review checked against {@code body}, or null when it has none. A
     * re-save of the same row keeps the server's verdict, not the copy the client sent back.
     */
    private LetterReview storedReview(GeneratedDocument existing, String body) {
        if (existing.structuredContent() == null) {
            return null;
        }
        try {
            JsonNode review = objectMapper.readTree(existing.structuredContent()).path("letterReview");
            return review.isObject() ? objectMapper.treeToValue(review, LetterReview.class).checkedAgainst(body) : null;
        } catch (JsonProcessingException unreadable) {
            return null;
        }
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
                document.atsReport(),
                document.letterReview());
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
