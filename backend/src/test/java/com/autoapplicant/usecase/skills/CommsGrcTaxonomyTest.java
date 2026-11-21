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
}
