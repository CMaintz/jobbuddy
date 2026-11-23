package com.autoapplicant.domain.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pairs as they appear across our sources (Lever slugs, Jobindex company names, Teamtailor
 * pages). A wrong merge is worse than a missed one: it files one employer's notes, research and
 * history under another, so the must-not-merge list matters as much as the must-merge one.
 */
class CompanyNamesTest {

    @ParameterizedTest(name = "\"{0}\" is the same employer as \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "veo | Veo Technologies",
            "Veo | Veo Technologies ApS",
            "Netcompany | NETCOMPANY A/S",
            "Bankdata | Bankdata A/S",
            "Goodiebox | Goodiebox ApS.",
            "Salling Group | Salling Group A/S",
            "Arla Foods | Arla Foods amba",
            "Arla Foods | Arla Foods a.m.b.a.",
            "Unity | Unity Technologies",
            "Pleo | Pleo Technologies A/S",
            "Too Good To Go | Too Good To Go ApS",
            "Spotify | Spotify AB",
            "Trustpilot | Trustpilot A/S",
            "Danske Bank | Danske Bank A/S",
    })
    void obviousDuplicatesShareAKey(String one, String other) {
        // Bug: a missed suffix ("ApS", "a.m.b.a.") or a case difference keeps the same
        // employer as two companies, splitting its notes and history.
        assertThat(CompanyNames.matchKey(one)).isEqualTo(CompanyNames.matchKey(other));
    }

    @ParameterizedTest(name = "\"{0}\" is not \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Netto | Netcompany",
            "Veo | Veovo",
            "Danske Bank | Danske Spil",
            "Novo Nordisk | Novo Nordisk Foundation",
            "Novo Nordisk | Novo Holdings A/S",
            "Salling Group | Salling Banking",
            "Lunar | Lunar Way",
            "Bankdata | Bank",
            "ATP | ATP Ejendomme",
            "Group One | Group Two",
    })
    void distinctCompaniesKeepTheirOwnKey(String one, String other) {
        // Bug: matching on a prefix or stripping words that are part of the name lets "Netto"
        // swallow "Netcompany" or a foundation swallow the company it owns.
        assertThat(CompanyNames.matchKey(one)).isNotEqualTo(CompanyNames.matchKey(other));
    }

    @ParameterizedTest(name = "\"{0}\" keeps a key")
    @CsvSource(delimiter = '|', value = {"A/S", "Technologies", "Technologies ApS"})
    void aNameMadeOnlyOfSuffixesIsNotStrippedToNothing(String name) {
        // Bug: stripping every token leaves an empty key, and every such name then merges
        // into the same company.
        assertThat(CompanyNames.matchKey(name)).isNotBlank();
    }
}
