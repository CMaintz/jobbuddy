package com.autoapplicant.adapter.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlSafetyValidatorTest {

    @Test
    void publicHttpUrlsAreSafe() {
        // IP literals avoid DNS so the test is deterministic/offline.
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://8.8.8.8/jobs/1")).isTrue();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("https://93.184.216.34/view")).isTrue();
    }

    @Test
    void loopbackAndPrivateAddressesAreBlocked() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://127.0.0.1/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://10.0.0.5/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://192.168.1.1/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://169.254.1.1/x")).isFalse();  // link-local
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[::1]/x")).isFalse();          // IPv6 loopback
    }

    @Test
    void nonHttpSchemesAndGarbageAreBlocked() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("ftp://8.8.8.8/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("file:///etc/passwd")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("not a url")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl(null)).isFalse();
    }
}
