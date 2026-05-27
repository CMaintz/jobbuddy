package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.GeneratedDocument;

import java.util.List;
import java.util.UUID;

public interface GetDocumentsForJobUseCase {
    List<GeneratedDocument> getDocumentsForJob(UUID jobId);
}
