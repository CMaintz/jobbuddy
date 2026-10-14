package com.autoapplicant.adapter.crawler;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.internal.StringUtil;

/**
 * Turns the URLs found in the wild (feed links, Location headers) into {@link URI}s as leniently
 * as Jsoup's own fetcher does. {@link URI#create} refuses a raw space or {@code |}, which real
 * job boards emit, and {@link URI#resolve} mangles a relative reference against a base with an
 * empty path ({@code http://a.com} + {@code x} gives {@code http://a.comx}). The SSRF check runs
 * on the URI produced here, so leniency never widens what may be fetched.
 */
final class LenientUrls {

    /** ASCII characters a URI may not carry raw; the same set Jsoup's UrlBuilder escapes. */
    private static final String UNSAFE_CHARACTERS = " <>\"{}|\\^`";
    private static final char ESCAPE = '%';
    private static final int HEX_RADIX = 16;
    private static final int ESCAPE_DIGITS = 2;
    private static final char FIRST_PRINTABLE = ' ';
    private static final char DELETE = 0x7f;

    private LenientUrls() {}

    /** An absolute URL as a URI, empty when even a lenient reading cannot make one. */
    static Optional<URI> parse(String url) {
        if (url == null) return Optional.empty();
        try {
            return Optional.of(URI.create(escapeUnsafe(url.trim())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** {@code reference} resolved against {@code base}, empty when it is not a usable URL. */
    static Optional<URI> resolve(URI base, String reference) {
        String resolved = StringUtil.resolve(base.toString(), reference.trim());
        return resolved.isEmpty() ? Optional.empty() : parse(resolved);
    }

    /** Percent-encodes characters a URI cannot hold raw, leaving valid escapes untouched. */
    static String escapeUnsafe(String url) {
        StringBuilder out = new StringBuilder(url.length());
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (mustEscape(url, i)) {
                out.append(ESCAPE).append(String.format(Locale.ROOT, "%02X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean mustEscape(String url, int index) {
        char c = url.charAt(index);
        if (c == ESCAPE) return !isEscapeSequence(url, index);
        return c < FIRST_PRINTABLE || c == DELETE || UNSAFE_CHARACTERS.indexOf(c) >= 0;
    }

    private static boolean isEscapeSequence(String url, int index) {
        if (index + ESCAPE_DIGITS >= url.length()) return false;
        return Character.digit(url.charAt(index + 1), HEX_RADIX) >= 0
                && Character.digit(url.charAt(index + 2), HEX_RADIX) >= 0;
    }
}
