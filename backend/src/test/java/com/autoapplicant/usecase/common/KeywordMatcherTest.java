package com.autoapplicant.usecase.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordMatcherTest {

    @Test
    void javascript_does_not_satisfy_java() {
        // The whole point of the exercise.
        assertThat(KeywordMatcher.contains("Built the frontend in JavaScript and TypeScript", "Java")).isFalse();
        assertThat(KeywordMatcher.contains("Backend services in Java 21", "Java")).isTrue();
    }

    @Test
    void a_keyword_ending_in_punctuation_matches_without_a_word_after_it() {
        assertThat(KeywordMatcher.contains("Wrote services in C# and F#", "C#")).isTrue();
        assertThat(KeywordMatcher.contains("Embedded work in C++", "C++")).isTrue();
        // ...but the letter before it still has to be a boundary.
        assertThat(KeywordMatcher.contains("Worked on OC# internals", "C#")).isFalse();
    }

    @Test
    void a_keyword_starting_with_punctuation_matches_inside_a_compound() {
        // ASP.NET genuinely is .NET; refusing that would under-report.
        assertThat(KeywordMatcher.contains("Ten years of ASP.NET Core", ".NET")).isTrue();
        assertThat(KeywordMatcher.contains("Migrated to .NET 8", ".NET")).isTrue();
        assertThat(KeywordMatcher.contains("Ran the .NETWORK team", ".NET")).isFalse();
    }

    @Test
    void a_dotted_keyword_still_refuses_a_longer_word() {
        assertThat(KeywordMatcher.contains("Backend in Node.js", "Node.js")).isTrue();
        assertThat(KeywordMatcher.contains("Wrote Node.jsx components", "Node.js")).isFalse();
    }

    @Test
    void a_single_letter_keyword_does_not_match_every_stray_letter() {
        assertThat(KeywordMatcher.contains("Statistical modelling in R and Python", "R")).isTrue();
        assertThat(KeywordMatcher.contains("Reporting and rollout across regions", "R")).isFalse();
    }

    @Test
    void a_multi_word_keyword_tolerates_any_whitespace_between_its_words() {
        assertThat(KeywordMatcher.contains("Applied machine learning to matching", "machine learning")).isTrue();
        assertThat(KeywordMatcher.contains("Applied machine\n  learning to matching", "machine learning")).isTrue();
        assertThat(KeywordMatcher.contains("A machine, and separately learning", "machine learning")).isFalse();
    }

    @Test
    void case_and_danish_letters_are_handled() {
        assertThat(KeywordMatcher.contains("Erfaring med KUBERNETES i produktion", "Kubernetes")).isTrue();
        assertThat(KeywordMatcher.contains("Ansvar for kvalitetssikring", "Kvalitetssikring")).isTrue();
        // Danish letters are word characters, so a boundary still applies around them.
        assertThat(KeywordMatcher.contains("Arbejdede med målstyring", "mål")).isFalse();
    }

    @Test
    void an_inflected_danish_form_is_a_known_miss_rather_than_a_silent_false_positive() {
        // Documented limitation: allowing a suffix here would let "he excels at"
        // satisfy "Excel", which is the worse error.
        assertThat(KeywordMatcher.contains("Godt samarbejdet med teamet", "Samarbejde")).isFalse();
        assertThat(KeywordMatcher.contains("He excels at delivery", "Excel")).isFalse();
    }

    @Test
    void nothing_to_match_against_is_not_a_match() {
        assertThat(KeywordMatcher.contains(null, "Java")).isFalse();
        assertThat(KeywordMatcher.contains("Java", null)).isFalse();
        assertThat(KeywordMatcher.contains("  ", "Java")).isFalse();
    }

    @Test
    void a_keyword_with_regex_characters_is_treated_as_text() {
        assertThat(KeywordMatcher.contains("Experience with A+B tooling", "A+B")).isTrue();
        assertThat(KeywordMatcher.contains("Experience with AAAB tooling", "A+B")).isFalse();
    }
}
