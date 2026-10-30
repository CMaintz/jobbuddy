package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.usecase.ai.RetractedClaimsGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GeneratedContentGuardsTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BODY = "the generated letter";
    private static final String PROFILE = "{\"profile\":true}";
    private static final String LETTER = "COVER_LETTER";

    private final DocumentFactGuard factGuard = mock(DocumentFactGuard.class);
    private final RetractedClaimsGuard retractedGuard = mock(RetractedClaimsGuard.class);
    private final ClicheGuard clicheGuard = mock(ClicheGuard.class);
    private final GeneratedContentGuards guards =
            new GeneratedContentGuards(factGuard, retractedGuard, clicheGuard);

    @BeforeEach
    void cleanTextAndWarnModeByDefault() {
        // Constructed outside Spring, so every @Value field starts at its Java default.
        ReflectionTestUtils.setField(guards, "factGuardEnabled", true);
        ReflectionTestUtils.setField(guards, "clicheGuardEnabled", true);
        modes("warn", "warn", "warn");
        facts(List.of(), List.of());
        when(retractedGuard.findViolations(USER, BODY)).thenReturn(List.of());
        cliches(List.of());
    }

    @Test
    void warnModeReportsEveryFindingWithoutBlocking() {
        facts(List.of("40% faster"), List.of("12 teams"));
        when(retractedGuard.findViolations(USER, BODY)).thenReturn(List.of("AWS certified"));
        cliches(List.of("team player"));

        ContentGuardFindings findings = guards.verify(USER, BODY, PROFILE, LETTER);

        assertThat(findings.unsupportedMetrics()).containsExactly("40% faster");
        assertThat(findings.unverifiedMetrics()).containsExactly("12 teams");
        assertThat(findings.retractedClaims()).containsExactly("AWS certified");
        assertThat(findings.fillerPhrases()).containsExactly("team player");
    }

    @Test
    void anInventedMetricBlocksInBlockMode() {
        modes("block", "warn", "warn");
        facts(List.of("40% faster"), List.of());

        assertThatThrownBy(() -> guards.verify(USER, BODY, PROFILE, LETTER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("40% faster")
                .hasMessageContaining("app.ai.fact-guard.mode=block");
    }

    @Test
    void anUnverifiedMetricNeverBlocksEvenInBlockMode() {
        modes("block", "warn", "warn");
        facts(List.of(), List.of("12 teams"));

        ContentGuardFindings findings = guards.verify(USER, BODY, PROFILE, LETTER);

        assertThat(findings.unverifiedMetrics()).containsExactly("12 teams");
        assertThat(findings.unsupportedMetrics()).isEmpty();
    }

    @Test
    void aResurfacedRetractedClaimBlocksInBlockMode() {
        modes("warn", "block", "warn");
        when(retractedGuard.findViolations(USER, BODY)).thenReturn(List.of("AWS certified"));

        assertThatThrownBy(() -> guards.verify(USER, BODY, PROFILE, LETTER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ai.retracted-claims.mode=block");
    }

    @Test
    void aClicheBlocksOnlyUnderItsOwnMode() {
        modes("block", "block", "warn");
        cliches(List.of("team player"));

        assertThat(guards.verify(USER, BODY, PROFILE, LETTER).fillerPhrases())
                .containsExactly("team player");

        modes("warn", "warn", "block");
        assertThatThrownBy(() -> guards.verify(USER, BODY, PROFILE, LETTER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ai.cliche-guard.mode=block");
    }

    @Test
    void aDisabledFactGuardNeitherBlocksNorReports() {
        ReflectionTestUtils.setField(guards, "factGuardEnabled", false);
        modes("block", "warn", "warn");
        facts(List.of("40% faster"), List.of("12 teams"));

        ContentGuardFindings findings = guards.verify(USER, BODY, PROFILE, LETTER);

        assertThat(findings.unsupportedMetrics()).isEmpty();
        assertThat(findings.unverifiedMetrics()).isEmpty();
    }

    @Test
    void aDisabledClicheGuardNeitherBlocksNorReports() {
        ReflectionTestUtils.setField(guards, "clicheGuardEnabled", false);
        modes("warn", "warn", "block");
        cliches(List.of("team player"));

        assertThat(guards.verify(USER, BODY, PROFILE, LETTER).fillerPhrases()).isEmpty();
    }

    @Test
    void aReadingSurfaceIsNotGuardedAtAll() {
        modes("block", "block", "block");
        facts(List.of("40% faster"), List.of("12 teams"));
        when(retractedGuard.findViolations(USER, BODY)).thenReturn(List.of("AWS certified"));
        cliches(List.of("team player"));

        assertThat(guards.verify(USER, BODY, PROFILE, "CV_ANALYSIS")).isEqualTo(ContentGuardFindings.NONE);
    }

    private void modes(String fact, String retracted, String cliche) {
        ReflectionTestUtils.setField(guards, "factGuardMode", fact);
        ReflectionTestUtils.setField(guards, "retractedClaimsMode", retracted);
        ReflectionTestUtils.setField(guards, "clicheGuardMode", cliche);
    }

    private void facts(List<String> invented, List<String> unverified) {
        when(factGuard.audit(BODY, PROFILE)).thenReturn(new DocumentFactGuard.FactAudit(invented, unverified));
    }

    private void cliches(List<String> phrases) {
        when(clicheGuard.audit(BODY)).thenReturn(new ClicheGuard.ClicheAudit(phrases));
    }
}
