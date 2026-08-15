package com.autoapplicant.adapter.crawler;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF guard for outbound fetches of externally-sourced URLs. Job/company URLs come from
 * untrusted crawled content, so before fetching one we require http(s) and reject hosts that
 * resolve to loopback, private, link-local, or otherwise internal addresses — preventing a
 * crafted posting from pointing the crawler at internal infrastructure.
 *
 * <p>Technique noted in the career-ops analysis (SSRF hardening + host allowlisting).
 */
public final class UrlSafetyValidator {

    private UrlSafetyValidator() {}

    /** True only when the URL is an http(s) URL whose host resolves solely to public addresses. */
    public static boolean isSafeHttpUrl(String url) {
        if (url == null || url.isBlank()) return false;
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) return false;
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isInternal(addr)) return false;
            }
        } catch (UnknownHostException e) {
            return false;
        }
        return true;
    }

    private static boolean isInternal(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()
                || isUniqueLocalIpv6(addr);
    }

    /** IPv6 unique-local addresses (fc00::/7) — private, not covered by isSiteLocalAddress. */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] b = addr.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }
}
