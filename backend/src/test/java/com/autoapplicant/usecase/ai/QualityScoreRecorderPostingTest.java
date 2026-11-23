package com.autoapplicant.usecase.ai;

import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.INVENTING_LETTER;
import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.POSTING;
import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.QUOTING_LETTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.RecordedQualityScore;
import com.autoapplicant.port.out.document.QualityScoreRepositoryPort;
import com.autoapplicant.usecase.document.ClicheGuard;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import com.autoapplicant.usecase.eval.DocumentQualityEvaluator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The recorded FACTS score does not charge a letter for quoting the posting it answers. */
class QualityScoreRecorderPostingTest {

    private final QualityScoreRepositoryPort repo = mock(QualityScoreRepositoryPort.class);
    private final QualityScoreRecorder recorder = new QualityScoreRecorder(
            new DocumentQualityEvaluator(new ClicheGuard(), new DocumentFactGuard()), repo);

    @Test
    void aPostingFigureQuotedAboutTheEmployerCostsNothing() {
        assertThat(factsScoreOf(QUOTING_LETTER)).isEqualTo(100);
    }

    @Test
    void anInventedFigureStillCostsTheFullPenalty() {
        assertThat(factsScoreOf(INVENTING_LETTER)).isEqualTo(50);
    }

    private int factsScoreOf(String letter) {
        recorder.record(command(), new GenerationInputs(null, null, "{}", POSTING), null, letter);
        ArgumentCaptor<RecordedQualityScore> saved = ArgumentCaptor.forClass(RecordedQualityScore.class);
        verify(repo).save(saved.capture());
        return saved.getValue().score().scoreOf(DocumentQualityEvaluator.FACTS);
    }

    private static GenerateDocumentCommand command() {
        return new GenerateDocumentCommand(UUID.randomUUID(), "COVER_LETTER", null, POSTING, null, null,
                null, null, "Danish", false, null, "STANDARD", null);
    }
}
