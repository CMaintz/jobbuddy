package com.autoapplicant.usecase.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.QualityScore;
import com.autoapplicant.usecase.document.ClicheGuard;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import com.autoapplicant.usecase.document.PromptCompositionBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The prompt evaluation harness: fixtures of (profile, posting, sample outputs) scored by
 * {@link DocumentQualityEvaluator}, so a prompt change can be judged by a number rather than by
 * reading one output and forming an impression.
 *
 * <p>Per fixture, the evaluator must separate a good output from a weak one by a wide margin,
 * which is what makes its score trustworthy as a regression metric. What the composed prompt
 * carries is pinned by {@code PromptCompositionBuilderGoldenTest}.
 *
 * <p>Everything runs offline. To score real generations, run the app's generation path and feed
 * the body to the same evaluator — the fixture scores below are the baseline to compare against.
 *
 * <p>Adding a fixture: drop a JSON file in {@code src/test/resources/prompt-eval/}. No code change.
 */
class PromptEvalHarnessTest {

    /** Fixture files, relative to {@code src/test/resources/prompt-eval/}. */
    private static final List<String> FIXTURES =
            List.of("da-backend-developer.json", "en-platform-engineer.json");

    /** A good output must clear this; below it, generation quality has regressed. */
    private static final int GOOD_FLOOR = 80;

    /** A weak output must stay below this. Deliberately not tuned to just above the current
     *  weak scores — the meaningful assertion is {@link #MIN_SEPARATION}, since a scorer is
     *  useful when it ranks, and an absolute cutoff on a hand-written sample is arbitrary. */
    private static final int WEAK_CEILING = 60;

    /** The gap that makes the score trustworthy as a regression metric. */
    private static final int MIN_SEPARATION = 30;

    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentQualityEvaluator evaluator =
            new DocumentQualityEvaluator(new ClicheGuard(), new DocumentFactGuard());
    private final PromptCompositionBuilder promptBuilder = new PromptCompositionBuilder();

    private record Fixture(String name, int targetWords, List<String> postingKeywords,
                           String profileJson, String good, String weak) {}

    private static List<Fixture> load(ObjectMapper mapper) throws IOException {
        List<Fixture> fixtures = new ArrayList<>();
        for (String file : FIXTURES) {
            try (InputStream in = PromptEvalHarnessTest.class
                    .getResourceAsStream("/prompt-eval/" + file)) {
                assertThat(in).as("fixture %s is on the test classpath", file).isNotNull();
                JsonNode n = mapper.readTree(in);
                List<String> keywords = new ArrayList<>();
                n.path("postingKeywords").forEach(k -> keywords.add(k.asText()));
                fixtures.add(new Fixture(
                        n.path("name").asText(), n.path("targetWords").asInt(),
                        keywords, n.path("profileJson").asText(),
                        n.path("outputs").path("good").asText(),
                        n.path("outputs").path("weak").asText()));
            }
        }
        return fixtures;
    }

    private QualityScore score(Fixture f, String output) {
        return evaluator.evaluate(output, f.profileJson(), f.postingKeywords(), f.targetWords());
    }

    @TestFactory
    Stream<DynamicTest> everyFixtureSeparatesGoodFromWeakOutput() throws IOException {
        return load(mapper).stream().map(f -> DynamicTest.dynamicTest(f.name(), () -> {
            QualityScore good = score(f, f.good());
            QualityScore weak = score(f, f.weak());

            // Printed so a prompt change can be compared run-to-run, not just asserted.
            System.out.printf("%-40s good=%3d weak=%3d%n", f.name(), good.total(), weak.total());
            print("good", good);
            print("weak", weak);

            assertThat(good.total()).as("good output for %s", f.name()).isGreaterThanOrEqualTo(GOOD_FLOOR);
            assertThat(weak.total()).as("weak output for %s", f.name()).isLessThanOrEqualTo(WEAK_CEILING);
            assertThat(good.total() - weak.total()).as("separation for %s", f.name())
                    .isGreaterThanOrEqualTo(MIN_SEPARATION);
        }));
    }

    private static void print(String label, QualityScore score) {
        score.dimensions().forEach(d -> System.out.printf("    %-4s %-9s %3d (w%d)  %s%n",
                label, d.code(), d.score(), d.weight(), d.detail()));
    }

    @TestFactory
    Stream<DynamicTest> eachDimensionPunishesTheFailureItExistsToCatch() throws IOException {
        return load(mapper).stream().map(f -> DynamicTest.dynamicTest(f.name(), () -> {
            QualityScore good = score(f, f.good());
            QualityScore weak = score(f, f.weak());

            // The weak samples are built from the exact failures the research names.
            assertThat(weak.scoreOf(DocumentQualityEvaluator.FILLER))
                    .as("filler").isLessThan(good.scoreOf(DocumentQualityEvaluator.FILLER));
            assertThat(weak.scoreOf(DocumentQualityEvaluator.CV_ECHO))
                    .as("CV echo").isLessThan(good.scoreOf(DocumentQualityEvaluator.CV_ECHO));
            assertThat(good.scoreOf(DocumentQualityEvaluator.FACTS))
                    .as("a good output invents no figures").isEqualTo(100);
        }));
    }

    @Test
    void thePromptAsksForTheWordCountTheScorerMeasures() {
        // Live scoring measures length against letterWordTarget; the prompt states its own number
        // in prose. If the two ever drift, a letter could follow its instructions exactly and still
        // be marked down for length.
        assertPromptAndTargetAgree("SHORT", 200);
        assertPromptAndTargetAgree("STANDARD", 300);
        assertPromptAndTargetAgree("DETAILED", 380);
        assertThat(PromptCompositionBuilder.letterWordTarget(null)).isEqualTo(300);
    }

    private void assertPromptAndTargetAgree(String lengthPreference, int words) {
        assertThat(PromptCompositionBuilder.letterWordTarget(lengthPreference))
                .as("scoring target for %s", lengthPreference).isEqualTo(words);
        PromptComposition c = promptBuilder.composeStructuredApplicationPrompt(
                "COVER_LETTER", "{}", PostingContext.ofDescription("posting text"), null, null,
                null, null, null, List.of(), null, lengthPreference);
        assertThat(c.userPromptTemplate()).as("prompt for %s", lengthPreference)
                .contains("about " + words + " words");
    }

    @Test
    void onlyProseLettersAreScored() {
        // Recruiter and follow-up messages carry their own word caps; scoring them against the
        // letter target would measure the wrong thing.
        assertThat(PromptCompositionBuilder.isProseLetter("COVER_LETTER")).isTrue();
        assertThat(PromptCompositionBuilder.isProseLetter("UNSOLICITED_APPLICATION")).isTrue();
        assertThat(PromptCompositionBuilder.isProseLetter("RECRUITER_MESSAGE")).isFalse();
        assertThat(PromptCompositionBuilder.isProseLetter(null)).isFalse();
    }

    @Test
    void anEmptyDocumentScoresBadlyRatherThanThrowing() {
        QualityScore score = evaluator.evaluate("", "{}", List.of("Java"), 300);
        assertThat(score.total()).isLessThan(WEAK_CEILING);
    }
}
