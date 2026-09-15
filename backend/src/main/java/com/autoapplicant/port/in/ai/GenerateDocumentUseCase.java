package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import java.util.concurrent.CompletableFuture;

public interface GenerateDocumentUseCase {

    CompletableFuture<StructuredDocument> generateDocument(GenerateDocumentCommand command);
}
