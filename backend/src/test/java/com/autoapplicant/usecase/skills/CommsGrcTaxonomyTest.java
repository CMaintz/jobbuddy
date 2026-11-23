package com.autoapplicant.usecase.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.adapter.persistence.adapter.SkillTaxonomyPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * The seeded taxonomy is what lets a Danish CV and a Danish posting meet on the same skill. A
 * communications or GRC profile writes "sociale medier" or "risikostyring", postings write the
 * English name or the other way round, and only a seeded row with that alias joins the two.
 */
@Import({SkillCanonicalizer.class, SkillTaxonomyPersistenceAdapter.class})
class CommsGrcTaxonomyTest extends PostgresIntegrationTest {

    @Autowired SkillCanonicalizer canonicalizer;

    @Test
    void danish_comms_wording_folds_onto_the_seeded_skill() {
        assertThat(canonicalizer.canonical("Sociale medier")).isEqualTo("social media");
        assertThat(canonicalizer.canonical("Intern kommunikation")).isEqualTo("internal communication");
        assertThat(canonicalizer.canonical("Krisekommunikation")).isEqualTo("crisis communication");
        assertThat(canonicalizer.canonical("Tekstforfatning")).isEqualTo("copywriting");
        assertThat(canonicalizer.canonical("Søgemaskineoptimering")).isEqualTo("seo");
    }

    @Test
    void danish_grc_wording_folds_onto_the_seeded_skill() {
        assertThat(canonicalizer.canonical("Informationssikkerhed")).isEqualTo("information security");
        assertThat(canonicalizer.canonical("Risikostyring")).isEqualTo("risk management");
        assertThat(canonicalizer.canonical("Risikovurdering")).isEqualTo("risk assessment");
        assertThat(canonicalizer.canonical("Databeskyttelse")).isEqualTo("data protection");
        assertThat(canonicalizer.canonical("Beredskabsplanlægning")).isEqualTo("business continuity");
        assertThat(canonicalizer.canonical("Governance, risk and compliance")).isEqualTo("grc");
    }

    @Test
    void the_danish_abbreviation_some_means_social_media_only_when_spelled_as_such() {
        // Enrichment returns posting skills as written, and Danish postings write "SoMe". The
        // lowercase word is ordinary English and must stay unknown.
        assertThat(canonicalizer.canonical("SoMe")).isEqualTo("social media");
        assertThat(canonicalizer.canonical("some")).isEqualTo("some");
    }

    @Test
    void the_dora_regulation_is_not_found_by_an_engineers_dora_metrics() {
        // Every surface form is searched as a whole token in a CV. A bare "dora" form would let
        // "DORA metrics" (deployment frequency, lead time) satisfy the EU regulation.
        assertThat(canonicalizer.surfaceForms("Digital Operational Resilience Act"))
                .map(String::toLowerCase)
                .doesNotContain("dora");
    }
}
