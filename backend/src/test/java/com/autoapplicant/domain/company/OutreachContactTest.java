package com.autoapplicant.domain.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutreachContactTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);

    @Test
    void anOpenThreadIsDueOnItsFollowUpDayAndAfterButNotBefore() {
        assertThat(contact(OutreachStatus.CONTACTED, TODAY).isFollowUpDue(TODAY)).isTrue();
        assertThat(contact(OutreachStatus.CONTACTED, TODAY.minusDays(3)).isFollowUpDue(TODAY)).isTrue();
        assertThat(contact(OutreachStatus.CONTACTED, TODAY.plusDays(1)).isFollowUpDue(TODAY)).isFalse();
    }

    @Test
    void aClosedThreadIsNeverDueEvenWithAPastFollowUpDate() {
        assertThat(contact(OutreachStatus.CLOSED, TODAY.minusDays(25)).isFollowUpDue(TODAY)).isFalse();
    }

    @Test
    void withoutAFollowUpDateNothingIsDue() {
        assertThat(contact(OutreachStatus.CONTACTED, null).isFollowUpDue(TODAY)).isFalse();
    }

    private static OutreachContact contact(OutreachStatus status, LocalDate followUpDue) {
        return new OutreachContact(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Acme A/S",
                status, "EMAIL", null, null, followUpDue, null, null, null);
    }
}
