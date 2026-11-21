package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.structured.LetterGatePoint;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.usecase.document.LetterGateTest.ScriptedModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The eval: a letter that echoes the posting and one that recaps the CV must both fail, and a
 * letter written from the employer's needs must pass. The answers replayed here are the real
 * gate's answers to these letters, recorded by {@link LetterGateLiveEvalTest}, so this checks that
 * our reading of a real answer reaches the verdict the gate meant.
 */
class LetterGateFixtureTest {

    private static final Path FIXTURES = Path.of("src/test/resources/letter-gate");

    private static LetterGateResult replay(String letter) throws IOException {
        ScriptedModel model = new ScriptedModel().answer(read("answer-" + letter + ".json"));
        LetterBrief brief = new LetterBrief("COVER_LETTER", read("posting-grc.txt"), "Danish", "DK");
        return new LetterGate(model, new ObjectMapper()).judge(read("letter-" + letter + ".txt"), brief);
    }

    private static String read(String name) throws IOException {
        return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
    }

    private static boolean failed(LetterGateResult result, int point) {
        return result.failedPoints().stream().anyMatch(p -> p.point() == point);
    }

    @Test
    void aLetterThatEchoesThePostingFailsOnPostingEchoWithTheEchoQuoted() throws IOException {
        // Bug: the echo letter passes because point 13's quotes or verdict are misread.
        LetterGateResult result = replay("echo");

        assertThat(result.verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(failed(result, 13)).isTrue();
        assertThat(result.failedPoints()).flatExtracting(LetterGatePoint::quotes)
                .anyMatch(quote -> quote.contains("I søger en compliance- og GRC-konsulent"));
    }

    @Test
    void aLetterThatRecapsTheCvFailsOnCvRecap() throws IOException {
        // Bug: the per-paragraph past-tense counts are misread and a CV walk-through passes.
        LetterGateResult result = replay("recap");

        assertThat(result.verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(failed(result, 14)).isTrue();
    }

    @Test
    void aLetterWrittenFromTheNeedsPasses() throws IOException {
        // Bug: a rule stricter than the guide (or a misread count) fails every letter, so each one
        // is rewritten three times and the user always sees a fail.
        assertThat(replay("compliant").verdict()).isEqualTo(LetterGateVerdict.PASSED);
    }
}
