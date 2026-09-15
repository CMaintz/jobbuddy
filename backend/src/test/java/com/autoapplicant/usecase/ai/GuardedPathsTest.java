package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every path that hands the user text they will send must run the content guards.
 *
 * <p>Generation was guarded from the start; refinement and review were not, for no reason anyone
 * chose — they were simply added later and nobody noticed the omission. "Make it stronger" was
 * therefore an unchecked invitation to invent, on the one path where the user is least likely to
 * re-read the output because they only asked for a tweak.
 *
 * <p>Asserting this structurally rather than behaviourally is deliberate: a unit test per path would
 * need the whole AI stack mocked, and the property that actually matters is "every service that
 * returns delivered text still calls the guards". When the four AI operations lived in one class this
 * was one file; now each surface is its own service, so the list is explicit — a new text-producing
 * service must be added here (and, of course, must guard its output).
 */
class GuardedPathsTest {

    private static final Path SRC = Path.of("src/main/java/com/autoapplicant");

    /** Every service that returns text destined for an employer. */
    private static final List<Path> TEXT_PRODUCING_SERVICES = List.of(
            SRC.resolve("usecase/ai/DocumentRefinementService.java"),
            SRC.resolve("usecase/ai/DocumentReviewService.java"),
            SRC.resolve("usecase/ai/AiService.java"), // generateDocument
            SRC.resolve("usecase/document/StructuredDocumentService.java")); // tailored CV

    @Test
    void everyTextProducingServiceRunsTheContentGuards() throws IOException {
        for (Path service : TEXT_PRODUCING_SERVICES) {
            String source = Files.readString(service);
            assertThat(source)
                    .as("%s returns delivered text and must guard it with contentGuards.verify(", service)
                    .contains("contentGuards.verify(");
        }
    }

    @Test
    void refinementCarriesTheHonestyRuleThatMakesItSafe() throws IOException {
        String source = Files.readString(SRC.resolve("usecase/ai/DocumentRefinementService.java"));
        // The specific failure this closes: "stronger" read as "claim more".
        assertThat(source).contains("never to claim more");
    }
}
