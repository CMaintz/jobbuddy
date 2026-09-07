package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.RefineDocumentRequest;
import com.autoapplicant.domain.ai.RefineDocumentResult;

import java.util.concurrent.CompletableFuture;

public interface RefineDocumentUseCase {
    CompletableFuture<RefineDocumentResult> refine(RefineDocumentRequest request);
}
