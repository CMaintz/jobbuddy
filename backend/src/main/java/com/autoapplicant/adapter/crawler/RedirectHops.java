package com.autoapplicant.adapter.crawler;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The per-hop rules shared by the hand-rolled redirect followers: which statuses redirect, how a
 * Location resolves, when to stop, and which headers must not follow a hop to another origin.
 */
final class RedirectHops {

    private static final Logger log = LoggerFactory.getLogger(RedirectHops.class);

    /** Redirects followed before giving up; real job boards need two or three at most. */
    static final int MAX_REDIRECT_HOPS = 5;

    static final String LOCATION_HEADER = "Location";

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    /** Lower-cased; these would hand one site's credentials to whatever host it redirects to. */
    static final Set<String> CREDENTIAL_HEADERS = Set.of("authorization", "cookie", "proxy-authorization");

    private RedirectHops() {}

    static boolean isRedirect(int status) {
        return REDIRECT_STATUSES.contains(status);
    }

    /** False, with a warning, when {@code target} is past the hop cap or fails {@code guard}. */
    static boolean mayFetch(Predicate<String> guard, URI target, int hop) {
        if (hop > MAX_REDIRECT_HOPS) {
            log.warn("Fetch gave up after {} redirects at {}", MAX_REDIRECT_HOPS, target);
            return false;
        }
        if (!guard.test(target.toString())) {
            log.warn("Fetch refused unsafe/non-public URL at hop {}: {}", hop, target);
            return false;
        }
        return true;
    }

    /**
     * Where a redirect from {@code from} points. Empty, with a warning, when the Location header
     * is missing or malformed: the caller must treat that as a failed fetch, because the 3xx
     * itself says nothing about the page it was meant to lead to.
     */
    static Optional<URI> target(URI from, Optional<String> location) {
        if (location.isEmpty()) {
            log.warn("Redirect from {} has no Location", from);
            return Optional.empty();
        }
        Optional<URI> target = LenientUrls.resolve(from, location.get());
        if (target.isEmpty()) log.warn("Redirect from {} has a malformed Location: {}", from, location.get());
        return target;
    }

    /** Parses a start URL, empty when it is not a URI at all. */
    static Optional<URI> parse(String url) {
        Optional<URI> uri = LenientUrls.parse(url);
        if (uri.isEmpty()) log.warn("Fetch refused malformed URL: {}", url);
        return uri;
    }

    static boolean sameOrigin(URI a, URI b) {
        return lowerCase(a.getScheme()).equals(lowerCase(b.getScheme()))
                && lowerCase(a.getHost()).equals(lowerCase(b.getHost()))
                && a.getPort() == b.getPort();
    }

    static boolean isCredentialHeader(String name) {
        return CREDENTIAL_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }

    private static String lowerCase(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT);
    }
}
