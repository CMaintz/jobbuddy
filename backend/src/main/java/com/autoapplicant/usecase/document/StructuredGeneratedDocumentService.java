package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.document.PersistGeneratedDocumentUseCase;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StructuredGeneratedDocumentService implements PersistGeneratedDocumentUseCase {

    private final GeneratedDocumentRepositoryPort documents;
    private final GeneratedDocumentWriter writer;

    public StructuredGeneratedDocumentService(GeneratedDocumentRepositoryPort documents,
                                              GeneratedDocumentWriter writer) {
        this.documents = documents;
        this.writer = writer;
    }

    @Override
    public StructuredDocument save(UUID userId, UUID jobId, StructuredDocument document, String modelUsed) {
        return writer.save(userId, jobId, document, modelUsed);
    }

    @Override
    public List<GeneratedDocument> listByUserId(UUID userId) {
        return documents.findByUserId(userId);
    }
}
