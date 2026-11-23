package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The Stash blob names the company for only the first few search pages. Every Jobindex RSS title
 * ends with ", Company", so the title is the fallback. The shapes below are real feed titles.
 */
class JobindexCompanyFromTitleTest {

    @Test
    void theCompanyIsWhatFollowsTheLastComma() {
        // Bug: splitting on the first comma takes part of the job title as the company.
        assertThat(JobindexConnector.companyFromRssTitle(
                "Senior Manager, Project Controlling, Semco Maritime A/S")).isEqualTo("Semco Maritime A/S");
        assertThat(JobindexConnector.companyFromRssTitle("Elev, JYSK")).isEqualTo("JYSK");
        assertThat(JobindexConnector.companyFromRssTitle(
                "Har du lyst til at arbejde som pædagogisk vikar i et dagtilbud, hvor mange vælger at "
                        + "blive lidt længere?, Furesø Kommune")).isEqualTo("Furesø Kommune");
        assertThat(JobindexConnector.companyFromRssTitle(
                "Mekaniker søges, Jacobs Auto v/Jacob Kenneth Jensen")).isEqualTo("Jacobs Auto v/Jacob Kenneth Jensen");
    }

    @Test
    void aTitleWithoutACompanyGivesNone() {
        // Bug: a title with no comma is taken whole as the company, so the job title shows up
        // as an employer.
        assertThat(JobindexConnector.companyFromRssTitle("Stock Controller")).isNull();
        assertThat(JobindexConnector.companyFromRssTitle("Chauffør, ")).isNull();
        assertThat(JobindexConnector.companyFromRssTitle(null)).isNull();
    }

    @Test
    void theStashCompanyWinsAndTheTitleFillsInWhenTheStashHasNone() {
        // Bug: the title fallback overrides the structured Stash name; or it is never used, so
        // postings past the first Stash pages keep no company.
        JobindexConnector.StashMeta withCompany =
                new JobindexConnector.StashMeta(null, "Whiteaway Group A/S", null, null, null);
        JobindexConnector.StashMeta withoutCompany =
                new JobindexConnector.StashMeta(null, null, null, null, null);
        String title = "Senior Sales Excellence Manager, Whiteaway Group";

        assertThat(JobindexConnector.companyOf(withCompany, title)).isEqualTo("Whiteaway Group A/S");
        assertThat(JobindexConnector.companyOf(withoutCompany, title)).isEqualTo("Whiteaway Group");
        assertThat(JobindexConnector.companyOf(null, title)).isEqualTo("Whiteaway Group");
    }
}
