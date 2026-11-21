package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateStop;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.domain.document.structured.LetterReview;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The gate's verdict describes the text it judged. A re-save keeps the stored verdict, marked stale
 * once the text has changed, and never takes the client's copy of it.
 */
class GeneratedDocumentWriterLetterReviewTest {

    private static final UUID USER = UUID.randomUUID();
    private static final String GATED = "Jeres chauffører skal følge reglerne.";
    private static final List<String> NEEDS = List.of("680 chauffører uden skærm skal følge reglerne");

    /** Keeps rows in memory, keyed by id. */
    static final class InMemoryDocuments implements GeneratedDocumentRepositoryPort {
        final Map<UUID, GeneratedDocument> rows = new HashMap<>();

        @Override
        public GeneratedDocument save(GeneratedDocument d) {
            UUID id = d.id() != null ? d.id() : UUID.randomUUID();
            GeneratedDocument saved = new GeneratedDocument(id, d.userId(), d.applicationId(), d.jobId(),
                    d.documentType(), d.content(), d.structuredContent(), d.templateId(), d.exportMode(),
                    d.promptTemplateId(), d.cvVersionId(), d.modelUsed(), d.tokensUsed(), d.createdAt());
            rows.put(id, saved);
            return saved;
        }

        @Override
        public List<GeneratedDocument> findByApplicationId(UUID applicationId) {
            return List.of();
        }

        @Override
        public List<GeneratedDocument> findByUserId(UUID userId) {
            return List.of();
        }

        @Override
        public List<GeneratedDocument> findByJobIdAndUserId(UUID jobId, UUID userId) {
            return List.of();
        }

        @Override
        public Optional<GeneratedDocument> findByIdAndUserId(UUID id, UUID userId) {
            return Optional.ofNullable(rows.get(id)).filter(d -> d.userId().equals(userId));
        }

        @Override
        public List<GeneratedDocument> findRecentByUserIdAndType(UUID userId, String documentType, int limit) {
            return List.of();
        }
    }

    private final ObjectMapper json = new ObjectMapper();
    private final InMemoryDocuments rows = new InMemoryDocuments();
    private final GeneratedDocumentWriter writer = new GeneratedDocumentWriter(rows, json);

    private static StructuredDocument letter(UUID id, String body, LetterReview review) {
        return new StructuredDocument(id, DocumentType.COVER_LETTER, "ATS", "classic", null, null, List.of(),
                body, null, review);
    }

    private static LetterReview failedReview() {
        return LetterReview.of(NEEDS,
                new LetterGateResult(LetterGateVerdict.FAILED, List.of(), 3, LetterGateStop.OUT_OF_ROUNDS), GATED);
    }

    private LetterReview stored(UUID id) throws Exception {
        return json.readValue(rows.rows.get(id).structuredContent(), StructuredDocument.class).letterReview();
    }

    @Test
    void aGeneratedLetterIsSavedWithItsReview() throws Exception {
        // Bug: the review is dropped on the way to the database, so the UI never shows the gate.
        UUID id = writer.save(USER, null, letter(null, GATED, failedReview()), "m").generatedDocumentId();

        assertThat(stored(id).needs()).isEqualTo(NEEDS);
        assertThat(stored(id).gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(stored(id).stale()).isFalse();
    }

    @Test
    void anEditedLetterKeepsItsVerdictMarkedStale() throws Exception {
        // Bug: after the user edits the letter, the old verdict is shown as if it judged the new text.
        UUID id = writer.save(USER, null, letter(null, GATED, failedReview()), "m").generatedDocumentId();

        StructuredDocument returned =
                writer.save(USER, null, letter(id, GATED + " Ny sætning.", failedReview()), "manual-edit");

        assertThat(stored(id).stale()).isTrue();
        assertThat(returned.letterReview().stale()).isTrue();
        assertThat(stored(id).gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void anEditSavedAsANewVersionCarriesTheVerdictMarkedStale() throws Exception {
        // Bug: the editor saves an edit as a new row with the old structure, and the new row shows
        // the old verdict as if the gate had judged the edited text, or drops it entirely.
        UUID id = writer.save(USER, null, letter(null, GATED + " Rettet.", failedReview()), "manual-edit")
                .generatedDocumentId();

        assertThat(stored(id).stale()).isTrue();
        assertThat(stored(id).gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void aResaveCannotReplaceTheVerdictWithTheClientsCopy() throws Exception {
        // Bug: the save endpoint trusts the review in the request body, so a client turns a failed
        // letter into a passed one; or it drops the review because the client did not send one.
        UUID id = writer.save(USER, null, letter(null, GATED, failedReview()), "m").generatedDocumentId();
        LetterReview forged = LetterReview.of(NEEDS,
                new LetterGateResult(LetterGateVerdict.PASSED, List.of(), 0, null), GATED);

        writer.save(USER, null, letter(id, GATED, forged), "manual-edit");
        assertThat(stored(id).gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);

        writer.save(USER, null, letter(id, GATED, null), "manual-edit");
        assertThat(stored(id).gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(stored(id).stale()).isFalse();
    }
}
