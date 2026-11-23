package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.ApplicationDocumentAiResponse;
import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.LetterReview;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.DocumentReviewer;
import com.autoapplicant.usecase.document.LetterBrief;
import com.autoapplicant.usecase.document.LetterGateLoop;
import com.autoapplicant.usecase.document.LetterRules;
import com.autoapplicant.usecase.document.PromptCompositionBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
 * runs the automatic critique/revise pass, {@link LetterGateLoop} gates a prose letter and rewrites it
 * while it fails, {@link GeneratedDocumentFinalizer} guards and persists, and
 * {@link QualityScoreRecorder} records the score. This class only sequences them.
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
    private final LetterGateLoop letterGate;

    public ApplicationDocumentService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                                      ObjectMapper objectMapper,
                                      ApplicationPromptAssembler promptAssembler,
                                      DocumentReviewer reviewer,
                                      GeneratedDocumentFinalizer finalizer,
                                      QualityScoreRecorder qualityRecorder,
                                      LetterGateLoop letterGate) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.promptAssembler = promptAssembler;
        this.reviewer = reviewer;
        this.finalizer = finalizer;
        this.qualityRecorder = qualityRecorder;
        this.letterGate = letterGate;
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
            List<String> needs = LetterRules.needs(aiResponse.needs());
            // Automatic drafter→reviewer loop before assembly. Config-gated; stops early once a pass
            // reports no further critique.
            String reviewed = reviewer.autoReview(reviewContext(command, inputs, needs), aiResponse.body());
            // The gate runs on the reviewed text and the guards on the gated text, so whatever a
            // rewrite changed is still checked against the profile.
            LetterGateLoop.Outcome gated = gate(command, inputs, reviewed, needs);
            StructuredDocument saved = finalizer.finalizeDocument(
                    command, inputs, gated.body(), gated.review(), aiProvider.chatModelName());
            qualityRecorder.record(command, inputs, saved, gated.body());
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

    /**
     * Gates a prose letter written to a posting. A prose letter with no posting to echo keeps its
     * needs with no verdict, so the user still sees what it was written from; anything else passes
     * through without a review.
     */
    private LetterGateLoop.Outcome gate(GenerateDocumentCommand cmd, GenerationInputs in, String body,
                                        List<String> needs) {
        boolean hasPosting = in.jobDescription() != null && !in.jobDescription().isBlank();
        if (!PromptCompositionBuilder.isProseLetter(cmd.documentType())) {
            return new LetterGateLoop.Outcome(body, null);
        }
        if (!hasPosting) {
            return new LetterGateLoop.Outcome(body, needs.isEmpty() ? null : LetterReview.of(needs, null, body));
        }
        LetterBrief brief = new LetterBrief(cmd.documentType(), in.jobDescription(), cmd.targetLanguage(),
                country(in));
        Instant deadline = cmd.deadline() != null ? cmd.deadline() : Instant.MAX;
        return letterGate.run(body, needs, brief, deadline);
    }

    private static DocumentReviewer.ReviewContext reviewContext(GenerateDocumentCommand cmd, GenerationInputs in,
                                                                List<String> needs) {
        return new DocumentReviewer.ReviewContext(cmd.userId(), cmd.documentType(),
                in.jobDescription(), cmd.targetLanguage(), country(in), needs);
    }

    private static String country(GenerationInputs in) {
        return in.job() != null ? in.job().country() : null;
    }
}
