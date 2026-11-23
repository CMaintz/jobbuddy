package com.autoapplicant.usecase.ai;

import static org.mockito.Mockito.mock;

import com.autoapplicant.usecase.document.ClicheGuard;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import com.autoapplicant.usecase.document.GeneratedContentGuards;
import org.springframework.test.util.ReflectionTestUtils;

/** Real content guards with the fact gate on, the way the posting-figure path tests need them. */
final class PostingAwareGuardsFixture {

    /** A posting figure quoted about the employer: "I" in Danish text addresses the reader. */
    static final String POSTING = "Kommunen har ca. 680 medarbejdere fordelt på 3 lokationer.";
    static final String QUOTING_LETTER =
            "Med ca. 680 medarbejdere har I brug for overblik, og det kræver struktur i hverdagen. "
                    + "Den slags opgaver har fyldt det meste af min tid, og jeg glæder mig til at tage fat.";
    static final String INVENTING_LETTER = QUOTING_LETTER + " Jeg har selv ledet 40 medarbejdere.";

    private PostingAwareGuardsFixture() {}

    /** The fact gate in {@code mode} ("warn" or "block"); the other guards find nothing. */
    static GeneratedContentGuards guards(String mode) {
        GeneratedContentGuards guards = new GeneratedContentGuards(
                new DocumentFactGuard(), mock(RetractedClaimsGuard.class), new ClicheGuard());
        ReflectionTestUtils.setField(guards, "factGuardEnabled", true);
        ReflectionTestUtils.setField(guards, "factGuardMode", mode);
        return guards;
    }
}
