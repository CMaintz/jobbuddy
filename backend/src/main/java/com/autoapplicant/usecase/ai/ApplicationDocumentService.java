package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.ApplicationDocumentAiResponse;
import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.DocumentReviewer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Generates an application document — the pipeline that turns a request into a saved
 * {@link StructuredDocument}. Each phase is its own collaborator: {@link ApplicationPromptAssembler}
 * resolves the context and composes the prompt, the model draft is parsed here, {@link DocumentReviewer}
 * runs the automatic critique/revise pass, {@link GeneratedDocumentFinalizer} guards and persists,
 * and {@link QualityScoreRecorder} records the score. This class only sequences them.
 */
@Service
public class ApplicationDocumentService implements GenerateDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApplicationDocumentService.class);

    private final ChatProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final ApplicationPromptAssembler promptAssembler;
    private final DocumentReviewer reviewer;
    private final GeneratedDocumentFinalizer finalizer;
    private final QualityScoreRecorder qualityRecorder;

    public ApplicationDocumentService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                                      ObjectMapper objectMapper,
                                      ApplicationPromptAssembler promptAssembler,
                                      DocumentReviewer reviewer,
                                      GeneratedDocumentFinalizer finalizer,
                                      QualityScoreRecorder qualityRecorder) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.promptAssembler = promptAssembler;
        this.reviewer = reviewer;
        this.finalizer = finalizer;
        this.qualityRecorder = qualityRecorder;
    }

    @Override
    @Async("userAiTaskExecutor")
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "generateAndParse throws the checked JsonProcessingException; the catch "
                    + "also absorbs runtime failures so any generation error fails the returned "
                    + "future rather than escaping the async executor.")
    public CompletableFuture<StructuredDocument> generateDocument(GenerateDocumentCommand command) {
        try {
            GenerationInputs inputs = promptAssembler.assemble(command);
            ApplicationDocumentAiResponse aiResponse = generateAndParse(inputs.composition());
            // Automatic drafter→reviewer loop before assembly. Config-gated; stops early once a pass
            // reports no further critique.
            String body = reviewer.autoReview(reviewContext(command, inputs), aiResponse.body());
            StructuredDocument saved =
                    finalizer.finalizeDocument(command, inputs, body, aiProvider.chatModelName());
            qualityRecorder.record(command, inputs, saved, body);
            return CompletableFuture.completedFuture(saved);
        } catch (Exception e) {
            log.error("Structured document generation failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Calls the model, sanitises and extracts the JSON object, and validates the parsed draft. */
    private ApplicationDocumentAiResponse generateAndParse(PromptComposition composition)
            throws JsonProcessingException {
        String json = AiResponseParser.extractJsonObject(
                AiResponseParser.sanitize(aiProvider.generateJson(composition, AiOperations.DOCUMENT_GENERATION))
                        .trim());
        ApplicationDocumentAiResponse aiResponse =
                objectMapper.readValue(json, ApplicationDocumentAiResponse.class);
        if (aiResponse.body() == null || aiResponse.body().isBlank()) {
            throw new IllegalArgumentException("AI response did not include a document body");
        }
        return aiResponse;
    }

    private static DocumentReviewer.ReviewContext reviewContext(GenerateDocumentCommand cmd, GenerationInputs in) {
        return new DocumentReviewer.ReviewContext(cmd.userId(), cmd.documentType(),
                in.jobDescription(), cmd.targetLanguage(),
                in.job() != null ? in.job().country() : null);
    }
}
