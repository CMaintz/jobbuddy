package com.autoapplicant.adapter.chatplan;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.domain.document.GenerationSettings;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.application.CreateApplicationUseCase;
import com.autoapplicant.port.in.application.GetApplicationsUseCase;
import com.autoapplicant.port.in.document.GenerateTailoredCvUseCase;
import com.autoapplicant.port.in.document.PersistGeneratedDocumentUseCase;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Makes a tailored CV and a cover letter for one posting the way the app's quick apply does: on
 * her application for the posting (one is started if she has none), through the same generation
 * use cases, so the letter passes the same fact gate and both follow her length and voice. The
 * documents are saved for her to review in the app's own editor; nothing is exported or sent.
 */
@Component
@Profile("desktop")
public class DocumentGeneration {

    private static final String COVER_LETTER = "COVER_LETTER";

    /**
     * How much longer than the letter's own deadline its result is waited for. The pipeline stops
     * gating at the deadline and saves what it has; waiting exactly as long would give up on a
     * letter that is about to be saved and start the next posting beside it.
     */
    private static final Duration SAVE_GRACE = Duration.ofMinutes(2);

    private final GetApplicationsUseCase applications;
    private final CreateApplicationUseCase createApplication;
    private final GenerateTailoredCvUseCase tailoredCv;
    private final PersistGeneratedDocumentUseCase documents;
    private final GenerateDocumentUseCase letters;
    private final Clock clock;
    private final Duration timeout;

    public DocumentGeneration(GetApplicationsUseCase applications,
                              CreateApplicationUseCase createApplication,
                              GenerateTailoredCvUseCase tailoredCv,
                              PersistGeneratedDocumentUseCase documents,
                              GenerateDocumentUseCase letters,
                              Clock clock,
                              @Value("${app.ai.request-timeout-seconds:1080}") long timeoutSeconds) {
        this.applications = applications;
        this.createApplication = createApplication;
        this.tailoredCv = tailoredCv;
        this.documents = documents;
        this.letters = letters;
        this.clock = clock;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }


    /**
     * Writes the CV and then the letter for the posting.
     *
     * @throws IllegalStateException generation failed or ran past the time limit
     */
    void write(UUID userId, UUID postingId, GenerationSettings settings) {
        StructuredDocument cv = tailoredCv.generateTailoredCv(userId, postingId, null,
                settings.instructions(), null, settings.cvTemplate(), null, false, null,
                settings.lengthPreference());
        documents.save(userId, postingId, cv, null);
        awaitLetter(new GenerateDocumentCommand(userId, COVER_LETTER, postingId, null, null, null,
                settings.instructions(), null, null, false, null, settings.lengthPreference(),
                clock.instant().plus(timeout)));
    }

    /**
     * Her application for the posting, started as PREPARING if she has none. The app allows one
     * per posting; one past PREPARING is refused when proposed, so the documents land beside her
     * work in progress, never on one she has sent.
     */
    UUID applicationFor(UUID userId, UUID postingId) {
        return applications.getApplications(userId).stream()
                .filter(a -> postingId.equals(a.jobId()))
                .map(Application::id)
                .findFirst()
                .orElseGet(() -> createApplication.createApplication(new CreateApplicationCommand(
                        userId, postingId, null, null, null, ApplicationStatus.PREPARING, null,
                        null, null, null, null)).id());
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
