package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.port.in.document.GetDocumentsForJobUseCase;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentJobService implements GetDocumentsForJobUseCase {

    private final GeneratedDocumentRepositoryPort docRepo;

    public DocumentJobService(GeneratedDocumentRepositoryPort docRepo) {
        this.docRepo = docRepo;
    }

    @Override
    public List<GeneratedDocument> getDocumentsForJob(UUID jobId) {
        return docRepo.findByJobId(jobId);
    }
}
