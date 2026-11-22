package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterReview;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.document.RecheckLetterUseCase;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gates a saved letter again. One verdict and no rewrite: after an edit the text is the user's,
 * and rewriting it from the gate's quotes would throw their changes away.
 */
@Service
public class LetterRecheckService implements RecheckLetterUseCase {

    private final GeneratedDocumentRepositoryPort documents;
    private final JobRepositoryPort jobs;
    private final LetterGate gate;
    private final GeneratedDocumentWriter writer;
    private final ObjectMapper objectMapper;

    public LetterRecheckService(GeneratedDocumentRepositoryPort documents, JobRepositoryPort jobs,
                                LetterGate gate, GeneratedDocumentWriter writer, ObjectMapper objectMapper) {
        this.documents = documents;
        this.jobs = jobs;
        this.gate = gate;
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("userAiTaskExecutor")
    public CompletableFuture<StructuredDocument> recheck(UUID userId, UUID generatedDocumentId) {
        try {
            return CompletableFuture.completedFuture(judgeAndStore(userId, generatedDocumentId));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private StructuredDocument judgeAndStore(UUID userId, UUID generatedDocumentId) {
        GeneratedDocument row = documents.findByIdAndUserId(generatedDocumentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Generated document not found"));
        String type = row.documentType() != null ? row.documentType().name() : null;
        if (!PromptCompositionBuilder.isProseLetter(type)) {
            throw new IllegalStateException("Only a letter can be checked");
        }
        Job job = postingOf(row);
        StructuredDocument letter = structured(row);
        String body = letter.bodyContent() != null && !letter.bodyContent().isBlank()
                ? letter.bodyContent() : row.content();
        LetterGateResult verdict = gate.judge(body, new LetterBrief(type, job.descriptionClean(), null, job.country()));
        return writer.replaceLetterReview(row, letter, LetterReview.of(needsOf(letter), verdict, body));
    }

    private Job postingOf(GeneratedDocument row) {
        return (row.jobId() != null ? jobs.findById(row.jobId()) : Optional.<Job>empty())
                .filter(job -> job.descriptionClean() != null && !job.descriptionClean().isBlank())
                .orElseThrow(() -> new IllegalStateException("This letter has no posting to check it against"));
    }

    private StructuredDocument structured(GeneratedDocument row) {
        if (row.structuredContent() == null) {
            throw new IllegalStateException("This letter was saved without its structure");
        }
        try {
            return objectMapper.readValue(row.structuredContent(), StructuredDocument.class);
        } catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("This letter's stored structure cannot be read", unreadable);
        }
    }

    private static List<String> needsOf(StructuredDocument letter) {
        return letter.letterReview() != null ? letter.letterReview().needs() : List.of();
    }
}
