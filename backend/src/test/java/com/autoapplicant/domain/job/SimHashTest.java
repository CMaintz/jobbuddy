package com.autoapplicant.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

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
        // Bag-of-words: the same tokens in reverse order must hash identically, so a repost that
        // only shuffles sentences is still caught. A shingle/n-gram hash would fail this.
        List<String> words = Arrays.asList(JD.split("\\s+"));
        Collections.reverse(words);
        String reversed = String.join(" ", words);

        assertThat(SimHash.fingerprint(reversed)).isEqualTo(SimHash.fingerprint(JD));
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
