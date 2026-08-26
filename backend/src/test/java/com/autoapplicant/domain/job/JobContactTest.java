package com.autoapplicant.domain.job;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobContactTest {

    @Test
    void anEmptyExtractionBecomesNoContactAtAll() {
        assertThat(JobContact.ofNullable(null, null, null, null)).isNull();
        assertThat(JobContact.ofNullable("  ", "", "   ", null)).isNull();
    }

    @Test
    void fieldsAreTrimmedAndBlanksBecomeNull() {
        JobContact contact = JobContact.ofNullable("  Mette Hansen ", " ", "m@example.dk", null);
        assertThat(contact.name()).isEqualTo("Mette Hansen");
        assertThat(contact.title()).isNull();
        assertThat(contact.phone()).isNull();
    }

    @Test
    void displayFallsBackToTheBareNameWithoutATitle() {
        assertThat(JobContact.ofNullable("Mette Hansen", "afdelingsleder", null, null).display())
                .isEqualTo("Mette Hansen, afdelingsleder");
        assertThat(JobContact.ofNullable("Mette Hansen", null, null, null).display())
                .isEqualTo("Mette Hansen");
    }

    @Test
    void aPhoneOnlyContactIsWorthKeepingButNotWorthAddressing() {
        JobContact contact = JobContact.ofNullable(null, null, null, "12 34 56 78");
        assertThat(contact).isNotNull();
        assertThat(contact.hasName()).isFalse();
        assertThat(contact.display()).isEmpty();
    }
}
