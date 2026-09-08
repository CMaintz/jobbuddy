package com.autoapplicant.domain.job;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimHashTest {

    private static final String JD = """
            We are looking for a senior backend engineer to join our platform team in Copenhagen.
            You will design and operate distributed services in Java and Kotlin, own reliability and
            observability, and mentor other engineers. Requirements: strong experience with Spring Boot,
            PostgreSQL, Kubernetes, and event-driven architectures. We offer a competitive salary,
            flexible remote work, and a strong engineering culture.""";

    @Test
    void identicalTextHasIdenticalFingerprint() {
        assertThat(SimHash.fingerprint(JD)).isEqualTo(SimHash.fingerprint(JD));
    }

    @Test
    void wordOrderDoesNotChangeFingerprint() {
        // bag-of-words: reversing sentence order keeps the same tokens
        String reordered = "culture engineering strong a and work remote flexible salary competitive "
                + JD;
        long a = SimHash.fingerprint(JD);
        long b = SimHash.fingerprint(reordered);
        // a light prefix change perturbs few bits — still a near-duplicate
        assertThat(SimHash.hammingDistance(a, b)).isLessThan(12);
    }

    @Test
    void nearIdenticalRepostIsClose() {
        // same posting, company name / one line swapped — the agency-repost case
        String repost = JD.replace("our platform team in Copenhagen", "the platform team at Acme A/S in Aarhus");
        assertThat(SimHash.hammingDistance(SimHash.fingerprint(JD), SimHash.fingerprint(repost)))
                .isLessThanOrEqualTo(8);
    }

    @Test
    void unrelatedTextIsFar() {
        String other = """
                Experienced pastry chef wanted for a busy bakery in central Odense. You will bake breads
                and cakes from early morning, manage stock, and train apprentices. Must love flour, sugar,
                and long standing shifts. No programming required whatsoever.""";
        assertThat(SimHash.hammingDistance(SimHash.fingerprint(JD), SimHash.fingerprint(other)))
                .isGreaterThan(18);
    }

    @Test
    void tooShortReturnsZero() {
        assertThat(SimHash.fingerprint("Backend engineer wanted")).isZero();
    }
}
