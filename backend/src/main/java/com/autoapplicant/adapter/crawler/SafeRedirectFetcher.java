package com.autoapplicant.adapter.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a GET and follows redirects by hand, running {@link UrlSafetyValidator} on every hop.
 * The JDK client's own redirect following only lets us validate the first URL, so a public page
 * answering 302 to an internal host (127.0.0.1, 169.254.169.254, ...) would slip past the SSRF
 * guard. Here the client never follows redirects itself and each Location is checked first.
 */
public final class SafeRedirectFetcher {

    private static final Logger log = LoggerFactory.getLogger(SafeRedirectFetcher.class);

    /** Redirects followed before giving up; real job boards need two or three at most. */
    static final int MAX_REDIRECT_HOPS = 5;

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final String LOCATION_HEADER = "Location";

    private final HttpClient client;

    public SafeRedirectFetcher(HttpClient client) {
        this.client = client;
    }

    /** A client that never follows redirects on its own, for use with this fetcher. */
    public static HttpClient newNonRedirectingClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
    }

    /**
     * Sends {@code request}, following safe redirects. Empty when any hop is an unsafe URL or the
     * hop cap is exceeded; otherwise the final non-redirect response.
     */
    public Optional<HttpResponse<String>> send(HttpRequest request)
            throws IOException, InterruptedException {
        HttpRequest current = request;
        for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
            if (!UrlSafetyValidator.isSafeHttpUrl(current.uri().toString())) {
                log.warn("Fetch refused unsafe/non-public URL at hop {}: {}", hop, current.uri());
                return Optional.empty();
            }
            HttpResponse<String> response = client.send(current, HttpResponse.BodyHandlers.ofString());
            Optional<URI> next = redirectTarget(response, current.uri());
            if (next.isEmpty()) return Optional.of(response);
            current = HttpRequest.newBuilder(current, (name, value) -> true).uri(next.get()).GET().build();
        }
        log.warn("Fetch gave up after {} redirects: {}", MAX_REDIRECT_HOPS, request.uri());
        return Optional.empty();
    }

    /** The resolved Location of a redirect response, or empty when it is not a usable redirect. */
    private static Optional<URI> redirectTarget(HttpResponse<String> response, URI from) {
        if (!REDIRECT_STATUSES.contains(response.statusCode())) return Optional.empty();
        Optional<String> location = response.headers().firstValue(LOCATION_HEADER);
        if (location.isEmpty()) return Optional.empty();
        try {
            return Optional.of(from.resolve(location.get().trim()));
        } catch (IllegalArgumentException e) {
            log.debug("Ignoring malformed redirect Location: {}", location.get());
            return Optional.empty();
        }
    }
}
