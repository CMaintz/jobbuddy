package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.LetterGatePoint;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateStop;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.domain.document.structured.LetterReview;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/**
 * A letter edited after generation carries a stale verdict, and the export warning offers to check
 * it again. The re-check has to judge the text the user has now, keep that text, and store a
 * verdict that no longer reads as stale.
 */
class LetterRecheckServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final String POSTING = "Vi søger en compliance-specialist til vores 680 chauffører.";
    private static final String GATED = "Jeres chauffører skal følge reglerne.";
    private static final String EDITED = GATED + " Jeg sikrer, at revisionen ikke finder huller.";
    private static final List<String> NEEDS = List.of("Revisionen må ikke finde huller");
    private static final LetterGateResult PASSED = new LetterGateResult(LetterGateVerdict.PASSED, List.of(), 0, null);
    private static final LetterGateResult FAILED = new LetterGateResult(LetterGateVerdict.FAILED,
            List.of(new LetterGatePoint(13, false, List.of(GATED), "Opremser opslaget.")), 0, null);

    private final ObjectMapper json = new ObjectMapper();
    private final GeneratedDocumentWriterLetterReviewTest.InMemoryDocuments rows =
            new GeneratedDocumentWriterLetterReviewTest.InMemoryDocuments();
    private final LetterGate gate = mock(LetterGate.class);
    private final JobRepositoryPort jobs = mock(JobRepositoryPort.class);
    private final LetterRecheckService service =
            new LetterRecheckService(rows, jobs, gate, new GeneratedDocumentWriter(rows, json), json);

    LetterRecheckServiceTest() {
        when(jobs.findById(JOB_ID)).thenReturn(Optional.of(
                Job.builder().id(JOB_ID).title("Compliance-specialist").descriptionClean(POSTING).build()));
        when(gate.judge(any(), any())).thenReturn(FAILED);
    }

    /** A stored letter edited since the gate failed it, so its review is stale. */
    private UUID editedLetter(UUID jobId) throws Exception {
        LetterReview stale = LetterReview.of(NEEDS,
                new LetterGateResult(LetterGateVerdict.FAILED, List.of(), 3, LetterGateStop.OUT_OF_ROUNDS), GATED)
                .checkedAgainst(EDITED);
        StructuredDocument letter = new StructuredDocument(null, DocumentType.COVER_LETTER, "ATS", "classic",
                null, null, List.of(), EDITED, null, stale);
        return rows.save(new GeneratedDocument(null, USER, null, jobId, DocumentType.COVER_LETTER, EDITED,
                json.writeValueAsString(letter), "classic", "ATS", null, null, "manual-edit", null,
                Instant.now())).id();
    }

    private StructuredDocument stored(UUID id) throws Exception {
        return json.readValue(rows.rows.get(id).structuredContent(), StructuredDocument.class);
    }

    @Test
    void aRecheckJudgesTheEditedTextAgainstThePostingAndClearsStale() throws Exception {
        // Bug: the re-check goes through the normal save, which keeps the stored verdict, so the
        // letter stays stale and failed however often the user checks it again.
        when(gate.judge(eq(EDITED), argThat(brief -> POSTING.equals(brief.posting())))).thenReturn(PASSED);
        UUID id = editedLetter(JOB_ID);

        StructuredDocument returned = service.recheck(USER, id).join();

        LetterReview review = stored(id).letterReview();
        assertThat(review.stale()).isFalse();
        assertThat(review.gate().verdict()).isEqualTo(LetterGateVerdict.PASSED);
        assertThat(review.needs()).isEqualTo(NEEDS);
        assertThat(returned.letterReview().gate().verdict()).isEqualTo(LetterGateVerdict.PASSED);
    }

    @Test
    void aFailedRecheckKeepsTheUsersTextAndShowsWhatFailed() throws Exception {
        // Bug: running the generation loop here rewrites the letter, replacing the user's own edits
        // with a model rewrite they never asked for.
        UUID id = editedLetter(JOB_ID);

        service.recheck(USER, id).join();

        assertThat(stored(id).bodyContent()).isEqualTo(EDITED);
        assertThat(rows.rows.get(id).content()).isEqualTo(EDITED);
        assertThat(stored(id).letterReview().gate().failedPoints()).extracting(LetterGatePoint::point)
                .containsExactly(13);
        assertThat(stored(id).letterReview().stale()).isFalse();
    }

    @Test
    void anotherUsersLetterCannotBeRechecked() throws Exception {
        // Bug: looking the document up by id alone lets one user spend model calls on, and
        // overwrite the verdict of, another user's letter.
        UUID id = editedLetter(JOB_ID);
        String before = rows.rows.get(id).structuredContent();

        assertThat(service.recheck(UUID.randomUUID(), id))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
        assertThat(rows.rows.get(id).structuredContent()).isEqualTo(before);
    }

    @Test
    void aLetterWithoutAPostingIsRefusedRatherThanJudgedAgainstNothing() throws Exception {
        // Bug: the gate is asked with "(intet opslag)" and the answer is stored as a real verdict,
        // a pass for a letter that was never checked against any posting.
        when(gate.judge(any(), any())).thenReturn(PASSED);
        UUID id = editedLetter(null);

        assertThat(service.recheck(USER, id))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalStateException.class);
        assertThat(stored(id).letterReview().stale()).isTrue();
    }
}
