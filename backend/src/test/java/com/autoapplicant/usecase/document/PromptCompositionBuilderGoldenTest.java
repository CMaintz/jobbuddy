package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.autoapplicant.domain.document.CompanyContext;
import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.document.PromptComposition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden pins for the two big generation prompts — the cover-letter/application prompt and the
 * CV-tailoring prompt. These are the largest, highest-leverage prompts in the app, and they were
 * unguarded: a stray edit to a guardrail block, the JSON schema, or the section order silently
 * changes what every user's letter or CV is generated from, and no other test would notice.
 *
 * <p>The {@code .golden.txt} files ARE the pin. An intentional prompt change updates them (delete the
 * file and re-run to regenerate, then eyeball the diff before committing); an accidental one fails
 * here. The builder is stateless, so the inputs below fully determine the output — market resolves to
 * Denmark from the "DK" country, language to English from the explicit choice.
 */
class PromptCompositionBuilderGoldenTest {

    private final PromptCompositionBuilder builder = new PromptCompositionBuilder();

    private static final String PROFILE_JSON =
            "{\"summary\":\"Backend engineer, 6 years\",\"skills\":[\"Java\",\"Spring\",\"PostgreSQL\"],"
            + "\"experience\":[{\"title\":\"Senior Engineer\",\"company\":\"Acme\"}]}";

    @Test
    void coverLetterPromptIsPinned() throws IOException {
        PostingContext posting = new PostingContext(
                "We're hiring a backend engineer with Java and Spring. Danish is a plus.",
                "DK", "Mette Hansen, afdelingsleder");
        PromptComposition composition = builder.composeStructuredApplicationPrompt(
                "COVER_LETTER", PROFILE_JSON, posting,
                "Keep it under one page.", "I admire the team's open-source work.", "English",
                null, null, List.of("Emphasise the ML projects next time."),
                new CompanyContext("Acme builds developer tooling; founded 2010; HQ in Copenhagen.", null),
                "STANDARD");
        assertMatchesGolden("generation-cover-letter", composition);
    }

    @Test
    void cvTailoringPromptIsPinned() throws IOException {
        PostingContext posting = new PostingContext(
                "We're hiring a backend engineer with Java and Spring.", "DK", null);
        PromptComposition composition = builder.composeCvTailoringPrompt(
                PROFILE_JSON, posting, "Lead with backend depth.", "English",
                null, null, List.of(), "STANDARD");
        assertMatchesGolden("cv-tailoring", composition);
    }

    /**
     * Pins both prompt channels. On first run (no golden yet) the actual output is written and the
     * test fails, prompting a human to review and commit the golden; thereafter any drift fails.
     */
    private static void assertMatchesGolden(String name, PromptComposition composition) throws IOException {
        String actual = "===== SYSTEM =====\n" + composition.systemPrompt()
                + "\n\n===== USER =====\n" + composition.userPromptTemplate();
        Path golden = Path.of("src/test/resources/prompts", name + ".golden.txt");
        if (!Files.exists(golden)) {
            Files.createDirectories(golden.getParent());
            Files.writeString(golden, actual);
            fail("Golden created at " + golden + " — review it, then re-run. Commit the .golden.txt.");
        }
        assertThat(actual)
                .as("Generation prompt '%s' changed. If intentional, delete %s and re-run to regenerate; "
                        + "otherwise a guardrail/schema/section edit drifted the delivered prompt.", name, golden)
                .isEqualTo(Files.readString(golden));
    }
}
