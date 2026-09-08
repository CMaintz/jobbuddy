package com.autoapplicant.usecase.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every path that hands the user text they will send must run the content guards.
 *
 * <p>Generation was guarded from the start; refinement and review were not, for no reason anyone
 * chose — they were simply added later and nobody noticed the omission. "Make it stronger" was
 * therefore an unchecked invitation to invent, on the one path where the user is least likely to
 * re-read the output because they only asked for a tweak.
 *
 * <p>Asserting this structurally rather than behaviourally is deliberate: a unit test per path
 * would need the whole AI stack mocked, and the property that actually matters is "nobody added a
 * fourth path and forgot".
 */
class GuardedPathsTest {

    private static final Path AI_SERVICE = Path.of(
            "src/main/java/com/autoapplicant/usecase/ai/AiService.java");

    /** Methods that return text destined for an employer. */
    private static final List<String> TEXT_PRODUCING_METHODS =
            List.of("refine(", "review(", "generateDocument(");

    @Test
    void everyTextProducingPathRunsTheContentGuards() throws IOException {
        String source = Files.readString(AI_SERVICE);
        long guardCalls = source.lines().filter(l -> l.contains("contentGuards.verify(")).count();

        assertThat(guardCalls)
                .as("each of %s must guard its output; found %d guard call(s)",
                        TEXT_PRODUCING_METHODS, guardCalls)
                .isGreaterThanOrEqualTo(TEXT_PRODUCING_METHODS.size());
    }

    @Test
    void refinementCarriesTheHonestyRuleThatMakesItSafe() throws IOException {
        String source = Files.readString(AI_SERVICE);
        // The specific failure this closes: "stronger" read as "claim more".
        assertThat(source).contains("never to claim more");
    }
}
