package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.ai.CliAgentAdapter;
import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The real check behind {@link LetterGateFixtureTest}: the three fixture letters through the local
 * Claude CLI. Opt-in, since it needs a logged-in {@code claude} and takes a minute or two:
 * {@code LETTER_GATE_LIVE=claude-cli ./gradlew :backend:test --tests "*LetterGateLiveEvalTest*"}.
 *
 * <p>Each raw answer is written to {@code build/letter-gate-live/}. Copying one into
 * {@code src/test/resources/letter-gate/} is a deliberate step, never automatic, so the replayed
 * answers stay real ones.
 */
@EnabledIfEnvironmentVariable(named = "LETTER_GATE_LIVE", matches = "claude-cli|codex|cli")
class LetterGateLiveEvalTest {

    private static final Path FIXTURES = Path.of("src/test/resources/letter-gate");
    private static final Path OUT = Path.of("build/letter-gate-live");

    /** Passes calls to the real provider and keeps the last raw answer. */
    static final class Recording implements ChatProviderPort {
        final ChatProviderPort real;
        String last;

        Recording(ChatProviderPort real) {
            this.real = real;
        }

        @Override
        public AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation) {
            AiCompletion completion = real.complete(composition, jsonObject, operation);
            last = completion.text();
            return completion;
        }

        @Override
        public String chatModelName() {
            return real.chatModelName();
        }
    }

    @ParameterizedTest
    @CsvSource({"echo, FAILED", "recap, FAILED", "compliant, PASSED"})
    void theRealGateJudgesTheFixtureLetter(String letter, LetterGateVerdict expected) throws IOException {
        Recording model = new Recording(new CliAgentAdapter(new AppProperties(), System.getenv("LETTER_GATE_LIVE")));
        LetterBrief brief = new LetterBrief("COVER_LETTER", read("posting-grc.txt"), "Danish", "DK");

        LetterGateResult result = new LetterGate(model, new ObjectMapper()).judge(read("letter-" + letter + ".txt"), brief);

        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("answer-" + letter + ".json"), model.last, StandardCharsets.UTF_8);
        assertThat(result.verdict()).as(model.last).isEqualTo(expected);
    }

    private static String read(String name) throws IOException {
        return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
    }
}
