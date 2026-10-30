package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.RetractedClaim;
import com.autoapplicant.port.out.user.RetractedClaimRepositoryPort;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetractedClaimsGuardTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final RetractedClaimRepositoryPort repo = mock(RetractedClaimRepositoryPort.class);
    private final RetractedClaimsGuard guard = new RetractedClaimsGuard(repo);

    @Test
    void findsAClaimRegardlessOfCase() {
        retracted("Led a team of 12");

        assertThat(guard.findViolations(USER, "In 2021 I LED A TEAM OF 12 engineers."))
                .containsExactly("Led a team of 12");
    }

    @Test
    void blankClaimsNeverMatch() {
        retracted("   ", null, "");

        assertThat(guard.findViolations(USER, "Any text at all.")).isEmpty();
    }

    @Test
    void aShortClaimDoesNotMatchInsideALongerWord() {
        retracted("Go");

        assertThat(guard.findViolations(USER, "A good communicator with strong algorithms.")).isEmpty();
    }

    @Test
    void aClaimBrokenAcrossALineStillMatches() {
        retracted("AWS certified architect");

        assertThat(guard.findViolations(USER, "I am an AWS\ncertified architect."))
                .containsExactly("AWS certified architect");
    }

    private void retracted(String... claims) {
        List<RetractedClaim> rows = Arrays.stream(claims)
                .map(claim -> new RetractedClaim(UUID.randomUUID(), USER, claim, null, null))
                .toList();
        when(repo.findByUserId(USER)).thenReturn(rows);
    }
}
