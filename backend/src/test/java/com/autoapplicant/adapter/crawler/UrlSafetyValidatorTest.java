package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
    void carrierGradeNatRangeIsBlockedIncludingCloudMetadata() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://100.100.100.200/latest/meta-data")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://100.64.0.1/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://100.127.255.255/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://100.63.255.255/x")).isTrue();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://100.128.0.0/x")).isTrue();
    }

    @Test
    void privateRangeOneSevenTwoIsBlockedOnlyWithinItsTwelveBitPrefix() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://172.16.0.1/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://172.31.255.255/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://172.15.255.255/x")).isTrue();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://172.32.0.1/x")).isTrue();
    }

    @Test
    void uniqueLocalIpv6IsBlocked() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[fc00::1]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[fdff:ffff::1]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[2001:4860:4860::8888]/x")).isTrue();
    }

    @Test
    void ipv4MappedIpv6IsJudgedAsTheIpv4ItMaps() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[::ffff:127.0.0.1]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[::ffff:a9fe:a9fe]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[::ffff:8.8.8.8]/x")).isTrue();
    }

    @Test
    void thisNetworkRangeIsBlocked() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://0.0.0.0/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://0.0.0.1/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://0.255.255.255/x")).isFalse();
    }

    @Test
    void nat64AddressesAreJudgedByTheirEmbeddedIpv4() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[64:ff9b::a9fe:a9fe]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[64:ff9b::7f00:1]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[64:ff9b::a00:1]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[64:ff9b::808:808]/x")).isTrue();
    }

    @Test
    void nat64LocalUsePrefixIsBlockedWholesale() {
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[64:ff9b:1::808:808]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[64:ff9b:1:ffff::1]/x")).isFalse();
        assertThat(UrlSafetyValidator.isSafeHttpUrl("http://[64:ff9b:2::808:808]/x")).isTrue();
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
