package com.autoapplicant.adapter.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Sends a GET and follows redirects by hand, running {@link UrlSafetyValidator} on every hop.
 * The JDK client's own redirect following only lets us validate the first URL, so a public page
 * answering 302 to an internal host (127.0.0.1, 169.254.169.254, ...) would slip past the SSRF
 * guard. Here the client never follows redirects itself and each Location is checked first.
 *
 * <p>Residual risk, DNS rebinding: the validator resolves the host, then the client resolves it
 * again to connect, so a host whose DNS answer flips between the two lookups could still reach
 * an internal address. Pinning the validated address is not clean with java.net.http (Host is a
 * restricted header, and connecting to a bare IP breaks SNI and certificate checks for https).
 * What closes the window in practice is the JVM's positive DNS cache
 * ({@code networkaddress.cache.ttl}, 30s by default): the client's lookup is served the answer
 * the validator just cached. Setting that TTL to 0 reopens the window.
 */
public final class SafeRedirectFetcher {

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
     * Sends {@code request}, following safe redirects. Empty when any hop is an unsafe URL, a
     * redirect has no usable Location, or the hop cap is exceeded; otherwise the final
     * non-redirect response.
     */
    public Optional<HttpResponse<String>> send(HttpRequest request)
            throws IOException, InterruptedException {
        return sendHop(request, 0);
    }

    private Optional<HttpResponse<String>> sendHop(HttpRequest current, int hop)
            throws IOException, InterruptedException {
        if (!RedirectHops.mayFetch(UrlSafetyValidator::isSafeHttpUrl, current.uri(), hop)) {
            return Optional.empty();
        }
        HttpResponse<String> response = client.send(current, HttpResponse.BodyHandlers.ofString());
        if (!RedirectHops.isRedirect(response.statusCode())) return Optional.of(response);
        Optional<URI> next = RedirectHops.target(
                current.uri(), response.headers().firstValue(RedirectHops.LOCATION_HEADER));
        if (next.isEmpty()) return Optional.empty();
        return sendHop(redirected(current, next.get()), hop + 1);
    }

    /** The follow-up GET to {@code to}; credentials are dropped once the hop leaves the origin. */
    private static HttpRequest redirected(HttpRequest from, URI to) {
        boolean sameOrigin = RedirectHops.sameOrigin(from.uri(), to);
        return HttpRequest.newBuilder(
                        from, (name, value) -> sameOrigin || !RedirectHops.isCredentialHeader(name))
                .uri(to)
                .GET()
                .build();
    }
}
