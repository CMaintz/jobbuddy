package com.autoapplicant.usecase.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClicheGuardTest {

    private final ClicheGuard guard = new ClicheGuard();

    @Test
    void concreteDanishTextIsClean() {
        assertThat(guard.audit("""
                Jeg byggede den ingest-pipeline, der i dag flytter 40.000 stillingsopslag om ugen \
                hos Jobindex. Det er præcis den slags dataarbejde, jeres nye team skal i gang med.""")
                .clean()).isTrue();
    }

    @Test
    void danishFormulaicOpenerIsCaught() {
        assertThat(guard.audit("Jeg søger hermed stillingen som backend-udvikler hos jer.").phrases())
                .contains("jeg søger hermed stillingen");
    }

    @Test
    void danishFloskelIsCaught() {
        // The subject varies ("jeg er"/"og er"/"som teamplayer"), so the bare floskel is the phrase.
        assertThat(guard.audit("Jeg er god til at holde mange bolde i luften og er en teamplayer.")
                .phrases()).contains("holde mange bolde i luften", "teamplayer");
        assertThat(guard.audit("Jeg brænder for at arbejde med data.").phrases())
                .contains("brænder for");
    }

    @Test
    void englishAiTellIsCaught() {
        assertThat(guard.audit("I am writing to apply for the position. I have a proven track record.")
                .phrases()).contains("i am writing to apply", "proven track record");
    }

    @Test
    void matchIsCaseAndWhitespaceInsensitive() {
        assertThat(guard.audit("I  Am\nWriting To Apply for this role").phrases())
                .contains("i am writing to apply");
    }

    @Test
    void curlyApostropheStillMatches() {
        assertThat(guard.audit("In today’s fast-paced world").phrases())
                .contains("in today's fast-paced");
    }

    @Test
    void emptyInputIsClean() {
        assertThat(guard.audit(null).clean()).isTrue();
        assertThat(guard.audit("   ").clean()).isTrue();
    }

    @Test
    void promptBlockCarriesOnlyTheRelevantLanguage() {
        String danish = ClicheGuard.promptBlock("Danish");
        assertThat(danish).contains("jeg søger hermed stillingen")
                .doesNotContain("proven track record");

        String english = ClicheGuard.promptBlock("English");
        assertThat(english).contains("proven track record")
                .doesNotContain("jeg søger hermed stillingen");

        // Unknown language: both lists, since either could turn up.
        String unknown = ClicheGuard.promptBlock(null);
        assertThat(unknown).contains("jeg søger hermed stillingen", "proven track record");
    }
}
