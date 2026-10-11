package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LenientUrlsTest {

    @Test
    void relativeReferenceAgainstAnEmptyPathBaseGetsItsSlash() {
        assertThat(LenientUrls.resolve(URI.create("http://a.com"), "x"))
                .contains(URI.create("http://a.com/x"));
    }

    @Test
    void rawSpacesAndPipesAreEscapedRatherThanRefused() {
        assertThat(LenientUrls.parse("http://a.com/job board|42?q=a b"))
                .contains(URI.create("http://a.com/job%20board%7C42?q=a%20b"));
        assertThat(LenientUrls.resolve(URI.create("http://a.com/jobs/"), "job 1"))
                .contains(URI.create("http://a.com/jobs/job%201"));
    }

    @Test
    void validEscapesAreKeptAndStrayPercentSignsEscaped() {
        assertThat(LenientUrls.escapeUnsafe("/caf%C3%A9/100%")).isEqualTo("/caf%C3%A9/100%25");
    }

    @Test
    void protocolRelativeLocationKeepsItsOwnHostForTheGuardToJudge() {
        Optional<URI> target = LenientUrls.resolve(URI.create("http://a.com/x"), "//127.0.0.1/admin");

        assertThat(target).contains(URI.create("http://127.0.0.1/admin"));
        assertThat(UrlSafetyValidator.isSafeHttpUrl(target.get().toString())).isFalse();
    }

    @Test
    void unusableUrlsAreEmpty() {
        assertThat(LenientUrls.resolve(URI.create("http://a.com/"), "http://[::1")).isEmpty();
        assertThat(LenientUrls.resolve(URI.create("http://a.com/"), "http://exa mple.com/")).isEmpty();
        assertThat(LenientUrls.parse(null)).isEmpty();
    }
}
