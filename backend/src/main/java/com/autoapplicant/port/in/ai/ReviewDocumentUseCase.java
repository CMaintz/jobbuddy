package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.ReviewDocumentRequest;
import com.autoapplicant.domain.ai.ReviewDocumentResult;

import java.util.concurrent.CompletableFuture;

/**
 * Second-pass reviewer: a fresh AI context critiques a drafted document against
 * the job posting and the user's writing profile, and returns a revised version.
 */
public interface ReviewDocumentUseCase {
    CompletableFuture<ReviewDocumentResult> review(ReviewDocumentRequest request);
}
