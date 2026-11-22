package com.autoapplicant.adapter.chatplan;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.GenerationSettings;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.document.GenerateTailoredCvUseCase;
import com.autoapplicant.port.in.document.PersistGeneratedDocumentUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Makes a tailored CV and a cover letter for one posting the way the app's quick apply does:
 * through the same generation use cases, so the letter passes the same fact gate and both follow her length, voice and
 * language. The documents are saved for her to review in the app's own editor; nothing is
 * exported or sent.
 */
@Component
@Profile("desktop")
public class DocumentGeneration {

    private static final String COVER_LETTER = "COVER_LETTER";
    private static final String DANSK = "Dansk";
    private static final String DANISH_PREFIX = "da";
    private static final String DANISH = "da";
    private static final String ENGLISH = "en";

    /**
     * How much longer than the letter's own deadline its result is waited for. The pipeline stops
     * gating at the deadline and saves what it has; waiting exactly as long would give up on a
     * letter that is about to be saved and start the next posting beside it.
     */
    private static final Duration SAVE_GRACE = Duration.ofMinutes(2);

    private final GenerateTailoredCvUseCase tailoredCv;
    private final PersistGeneratedDocumentUseCase documents;
    private final GenerateDocumentUseCase letters;
    private final GetJobByIdUseCase jobs;
    private final Clock clock;
    private final Duration timeout;

    public DocumentGeneration(GenerateTailoredCvUseCase tailoredCv,
                              PersistGeneratedDocumentUseCase documents,
                              GenerateDocumentUseCase letters,
                              GetJobByIdUseCase jobs,
                              Clock clock,
                              @Value("${app.ai.request-timeout-seconds:1080}") long timeoutSeconds) {
        this.tailoredCv = tailoredCv;
        this.documents = documents;
        this.letters = letters;
        this.jobs = jobs;
        this.clock = clock;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Writes the CV and then the letter for the posting.
     *
     * @throws IllegalStateException generation failed or ran past the time limit
     */
    void write(UUID userId, UUID postingId, GenerationSettings settings) {
        String language = languageFor(postingId, settings);
        StructuredDocument cv = tailoredCv.generateTailoredCv(userId, postingId, null,
                settings.instructions(), language, settings.cvTemplate(), null, false, null,
                settings.lengthPreference());
        documents.save(userId, postingId, cv, null);
        awaitLetter(new GenerateDocumentCommand(userId, COVER_LETTER, postingId, null, null, null,
                settings.instructions(), null, language, false, null, settings.lengthPreference(),
                clock.instant().plus(timeout)));
    }

    /**
     * Quick apply's choice: Danish for a posting written in Danish, otherwise her saved default.
     * Kept the same so her documents come out in one language wherever she makes them.
     */
    private String languageFor(UUID postingId, GenerationSettings settings) {
        boolean danishPosting = jobs.getJobById(postingId)
                .map(job -> job.languages() != null && job.languages().stream()
                        .anyMatch(l -> l.toLowerCase(Locale.ROOT).startsWith(DANISH_PREFIX)))
                .orElse(false);
        return danishPosting || DANSK.equals(settings.language()) ? DANISH : ENGLISH;
    }

    private void awaitLetter(GenerateDocumentCommand command) {
        try {
            letters.generateDocument(command).get(timeout.plus(SAVE_GRACE).toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Kept set, so the queue can tell a shutdown from a failure.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while the letter was written", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("The letter could not be written", e);
        }
    }
}
