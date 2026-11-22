package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.adapter.persistence.adapter.ApplicationPersistenceAdapter;
import com.autoapplicant.adapter.persistence.adapter.DocumentPersistenceAdapter;
import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.LetterChangedDuringCheckException;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateStop;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.domain.document.structured.LetterReview;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.application.ApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A recheck waits minutes on the gate, and the row can change meanwhile. Its write must touch only
 * the review, or it puts back the row as it read it. Runs outside the per-test transaction so each
 * write commits on its own, as the async recheck and the link request do in production.
 */
@Import({DocumentPersistenceAdapter.class, ApplicationPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LetterRecheckConcurrentChangeTest extends PostgresIntegrationTest {

    private static final String BODY = "Jeres chauffører skal følge reglerne.";

    @Autowired DocumentPersistenceAdapter documents;
    @Autowired ApplicationPersistenceAdapter applications;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private final LetterGate gate = mock(LetterGate.class);
    private final JobRepositoryPort jobs = mock(JobRepositoryPort.class);

    private UUID userId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", userId, userId + "@example.com");
        jobId = new JobRows(jdbc).insert("Compliance-specialist", "", Instant.now());
        when(jobs.findById(jobId)).thenReturn(Optional.of(
                Job.builder().id(jobId).title("Compliance-specialist").descriptionClean("680 chauffører").build()));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM generated_documents WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM applications WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        jdbc.update("DELETE FROM jobs WHERE id = ?", jobId);
    }

    @Test
    void aLinkMadeWhileTheGateRunsSurvivesTheRecheck() throws Exception {
        // Bug: the recheck saves the whole row from the copy it read before the gate call, so the
        // application link set in the meantime is reverted to null.
        ApplicationService applicationService = new ApplicationService(applications,
                mock(ApplicationStatusEventRepositoryPort.class), documents);
        UUID applicationId = applicationService.createApplication(userId, jobId, null, null).id();
        UUID letterId = staleLetter();
        when(gate.judge(any(), any())).thenAnswer(call -> {
            applicationService.attachGeneratedDocument(applicationId, userId, letterId, null, null, null);
            return new LetterGateResult(LetterGateVerdict.PASSED, List.of(), 0, null);
        });
        LetterRecheckService recheck = new LetterRecheckService(documents, jobs, gate,
                new GeneratedDocumentWriter(documents, json), json);

        recheck.recheck(userId, letterId).join();

        assertThat(jdbc.queryForObject("SELECT application_id FROM generated_documents WHERE id = ?",
                UUID.class, letterId)).isEqualTo(applicationId);
        LetterReview stored = json.readValue(jdbc.queryForObject(
                "SELECT structured_content FROM generated_documents WHERE id = ?", String.class, letterId),
                StructuredDocument.class).letterReview();
        assertThat(stored.gate().verdict()).isEqualTo(LetterGateVerdict.PASSED);
        assertThat(stored.stale()).isFalse();
    }

    @Test
    void anEditMadeWhileTheGateRunsIsKeptAndNotPassed() throws Exception {
        // Bug: the recheck writes back the structure it read, reverting the edit, or stores the
        // gate's pass as fresh for text the gate never saw.
        GeneratedDocumentWriter writer = new GeneratedDocumentWriter(documents, json);
        UUID letterId = staleLetter();
        String edited = BODY + " Jeg sikrer, at revisionen ikke finder huller.";
        when(gate.judge(any(), any())).thenAnswer(call -> {
            writer.save(userId, jobId, new StructuredDocument(letterId, DocumentType.COVER_LETTER, "ATS",
                    "classic", null, null, List.of(), edited, null, null), "manual-edit");
            return new LetterGateResult(LetterGateVerdict.PASSED, List.of(), 0, null);
        });
        LetterRecheckService recheck = new LetterRecheckService(documents, jobs, gate, writer, json);

        assertThat(recheck.recheck(userId, letterId)).failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(LetterChangedDuringCheckException.class);

        StructuredDocument stored = json.readValue(jdbc.queryForObject(
                "SELECT structured_content FROM generated_documents WHERE id = ?", String.class, letterId),
                StructuredDocument.class);
        assertThat(stored.bodyContent()).isEqualTo(edited);
        assertThat(stored.letterReview().gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void anOverlappingRecheckOfTheSameTextStillStoresItsVerdict() throws Exception {
        // Bug: the second check's write finds the first one's review in place and calls the
        // unchanged letter "changed", so a retry or a second tab gets a conflict.
        GeneratedDocumentWriter writer = new GeneratedDocumentWriter(documents, json);
        LetterRecheckService recheck = new LetterRecheckService(documents, jobs, gate, writer, json);
        UUID letterId = staleLetter();
        LetterGateResult failed = new LetterGateResult(LetterGateVerdict.FAILED, List.of(), 1, null);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        when(gate.judge(any(), any())).thenAnswer(call -> {
            if (firstCall.getAndSet(false)) {
                recheck.recheck(userId, letterId).join();
                return failed;
            }
            return new LetterGateResult(LetterGateVerdict.PASSED, List.of(), 0, null);
        });

        StructuredDocument returned = recheck.recheck(userId, letterId).join();

        LetterReview stored = json.readValue(jdbc.queryForObject(
                "SELECT structured_content FROM generated_documents WHERE id = ?", String.class, letterId),
                StructuredDocument.class).letterReview();
        assertThat(stored.gate()).isEqualTo(failed);
        assertThat(stored.stale()).isFalse();
        assertThat(returned.letterReview().gate()).isEqualTo(failed);
    }

    /** A saved letter whose review judged other text, so it shows as stale and wants a recheck. */
    private UUID staleLetter() throws Exception {
        LetterReview stale = LetterReview.of(List.of("Revisionen må ikke finde huller"),
                new LetterGateResult(LetterGateVerdict.FAILED, List.of(), 3, LetterGateStop.OUT_OF_ROUNDS),
                "En tidligere tekst.").checkedAgainst(BODY);
        StructuredDocument letter = new StructuredDocument(null, DocumentType.COVER_LETTER, "ATS", "classic",
                null, null, List.of(), BODY, null, stale);
        return documents.save(new GeneratedDocument(null, userId, null, jobId, DocumentType.COVER_LETTER, BODY,
                json.writeValueAsString(letter), "classic", "ATS", null, null, "manual-edit", null,
                Instant.now())).id();
    }
}
