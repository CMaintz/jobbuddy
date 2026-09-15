package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.ReviewDocumentRequest;
import com.autoapplicant.domain.ai.ReviewDocumentResult;
import com.autoapplicant.port.in.ai.ReviewDocumentUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.DocumentReviewer;
import com.autoapplicant.usecase.document.GeneratedContentGuards;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * The public "review my draft" endpoint: runs the shared {@link DocumentReviewer} pass, then applies
 * the same deterministic content guards a first draft gets — the reviewer rewrites the whole
 * document, so its output needs the same backstops. Split out of {@code AiService} so reviewing has
 * one reason to change, separate from analysis, refinement, and generation.
 */
@Service
public class DocumentReviewService implements ReviewDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DocumentReviewService.class);

    private final DocumentReviewer reviewer;
    private final GeneratedContentGuards contentGuards;
    private final CareerProfileContextService careerProfileContext;
    private final ChatProviderPort aiProvider;

    public DocumentReviewService(DocumentReviewer reviewer,
                                 GeneratedContentGuards contentGuards,
                                 CareerProfileContextService careerProfileContext,
                                 @Qualifier("generationAiProvider") ChatProviderPort aiProvider) {
        this.reviewer = reviewer;
        this.contentGuards = contentGuards;
        this.careerProfileContext = careerProfileContext;
        this.aiProvider = aiProvider;
    }

    @Override
    @Async("userAiTaskExecutor")
    public CompletableFuture<ReviewDocumentResult> review(ReviewDocumentRequest request) {
        try {
            DocumentReviewer.ReviewOutcome outcome = reviewer.review(
                    new DocumentReviewer.ReviewContext(request.userId(), request.documentType(),
                            request.jobDescription(), request.targetLanguage(), null),
                    request.currentContent());
            // The reviewer rewrites the whole document, so its output needs the same backstops as a
            // first draft — it was previously handed back unchecked.
            contentGuards.verify(request.userId(), outcome.revised(),
                    careerProfileContext.buildJson(request.userId()), request.documentType());
            return CompletableFuture.completedFuture(
                    new ReviewDocumentResult(outcome.revised(), outcome.critique(), aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Document review failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
