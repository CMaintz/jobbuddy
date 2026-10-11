package com.autoapplicant.adapter.crawler;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * SSRF guard for outbound fetches of externally-sourced URLs. Job/company URLs come from
 * untrusted crawled content, so before fetching one we require http(s) and reject hosts that
 * resolve to loopback, private, link-local, or otherwise internal addresses — preventing a
 * crafted posting from pointing the crawler at internal infrastructure.
 *
 * <p>Technique noted in an external reference implementation's analysis (SSRF hardening + host allowlisting).
 */
public final class UrlSafetyValidator {

    private static final int IPV4_BYTES = 4;
    private static final int IPV6_BYTES = 16;
    private static final int OCTET_MASK = 0xff;
    /** 0.0.0.0/8, "this network": not routable, but Linux connects 0.x addresses to localhost. */
    private static final int THIS_NETWORK_FIRST_OCTET = 0;
    /** 100.64.0.0/10, carrier-grade NAT. Clouds put metadata here (Alibaba: 100.100.100.200). */
    private static final int CGNAT_FIRST_OCTET = 100;
    private static final int CGNAT_SECOND_OCTET_MASK = 0xc0;
    private static final int CGNAT_SECOND_OCTET_PREFIX = 0x40;
    /** 64:ff9b::/96, the NAT64 well-known prefix; the last four bytes are an IPv4 address. */
    private static final byte[] NAT64_PREFIX = {
        0, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0
    };
    /**
     * 64:ff9b:1::/48, the NAT64 local-use prefix (RFC 8215). Where the IPv4 address sits depends
     * on the operator's chosen sub-prefix, and the range is never globally routed, so all of it
     * counts as internal.
     */
    private static final byte[] NAT64_LOCAL_USE_PREFIX = {0, 0x64, (byte) 0xff, (byte) 0x9b, 0, 1};

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
                || isUniqueLocalIpv6(addr)
                || isReservedIpv4(addr)
                || isNat64OfInternal(addr)
                || hasIpv6Prefix(addr.getAddress(), NAT64_LOCAL_USE_PREFIX);
    }

    /** IPv6 unique-local addresses (fc00::/7) — private, not covered by isSiteLocalAddress. */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] b = addr.getAddress();
        return b.length == IPV6_BYTES && (b[0] & 0xfe) == 0xfc;
    }

    /** 0.0.0.0/8 and 100.64.0.0/10, which the JDK's address predicates do not cover. */
    private static boolean isReservedIpv4(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length != IPV4_BYTES) return false;
        int first = b[0] & OCTET_MASK;
        boolean cgnat = first == CGNAT_FIRST_OCTET
                && (b[1] & CGNAT_SECOND_OCTET_MASK) == CGNAT_SECOND_OCTET_PREFIX;
        return first == THIS_NETWORK_FIRST_OCTET || cgnat;
    }

    /** A NAT64 address reaches the IPv4 address it embeds, so it is internal when that is. */
    private static boolean isNat64OfInternal(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (!hasIpv6Prefix(b, NAT64_PREFIX)) return false;
        try {
            return isInternal(InetAddress.getByAddress(Arrays.copyOfRange(b, NAT64_PREFIX.length, IPV6_BYTES)));
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private static boolean hasIpv6Prefix(byte[] address, byte[] prefix) {
        return address.length == IPV6_BYTES
                && Arrays.equals(address, 0, prefix.length, prefix, 0, prefix.length);
    }
}
