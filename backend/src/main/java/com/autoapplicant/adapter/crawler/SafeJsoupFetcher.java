package com.autoapplicant.adapter.crawler;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jsoup counterpart of {@link SafeRedirectFetcher}: a GET whose redirects are followed by hand,
 * with every hop SSRF-checked. Jsoup's own {@code followRedirects(true)} only lets a caller
 * validate the first URL, and crawled feed links are untrusted. The same DNS-rebinding caveat
 * applies as documented on {@link SafeRedirectFetcher}.
 */
public final class SafeJsoupFetcher {

    private static final Logger log = LoggerFactory.getLogger(SafeJsoupFetcher.class);

    /** The production fetcher, guarded by {@link UrlSafetyValidator}. */
    public static final SafeJsoupFetcher PUBLIC_ONLY =
            new SafeJsoupFetcher(UrlSafetyValidator::isSafeHttpUrl);

    private static final int FIRST_ERROR_STATUS = 400;

    private final Predicate<String> guard;

    SafeJsoupFetcher(Predicate<String> guard) {
        this.guard = guard;
    }

    /**
     * Fetches {@code url}, applying {@code configure} (user agent, headers, timeout) to the
     * connection of every hop. HTTP error statuses come back as responses, not exceptions.
     * Empty when a hop is unsafe, a redirect has no usable Location, or the hop cap is exceeded.
     */
    public Optional<Connection.Response> fetch(String url, UnaryOperator<Connection> configure)
            throws IOException {
        Optional<URI> start = RedirectHops.parse(url);
        if (start.isEmpty()) return Optional.empty();
        return fetchHop(start.get(), start.get(), configure, 0);
    }

    /** Like {@link #fetch}, parsed; also empty when the final status is an HTTP error. */
    public Optional<Document> fetchDocument(String url, UnaryOperator<Connection> configure)
            throws IOException {
        Optional<Connection.Response> response = fetch(url, configure);
        if (response.isEmpty()) return Optional.empty();
        if (response.get().statusCode() >= FIRST_ERROR_STATUS) {
            log.debug("HTTP {} fetching {}", response.get().statusCode(), url);
            return Optional.empty();
        }
        return Optional.of(response.get().parse());
    }

    private Optional<Connection.Response> fetchHop(
            URI origin, URI target, UnaryOperator<Connection> configure, int hop) throws IOException {
        if (!RedirectHops.mayFetch(guard, target, hop)) return Optional.empty();
        Connection.Response response = connect(origin, target, configure).execute();
        if (!RedirectHops.isRedirect(response.statusCode())) return Optional.of(response);
        Optional<URI> next = RedirectHops.target(
                target, Optional.ofNullable(response.header(RedirectHops.LOCATION_HEADER)));
        if (next.isEmpty()) return Optional.empty();
        return fetchHop(origin, next.get(), configure, hop + 1);
    }

    /** A connection for one hop; credentials the caller set are dropped off the origin. */
    private static Connection connect(URI origin, URI target, UnaryOperator<Connection> configure) {
        Connection connection = configure.apply(Jsoup.connect(target.toString()))
                .followRedirects(false)
                .ignoreHttpErrors(true);
        if (!RedirectHops.sameOrigin(origin, target)) dropCredentials(connection.request());
        return connection;
    }

    private static void dropCredentials(Connection.Request request) {
        RedirectHops.CREDENTIAL_HEADERS.forEach(request::removeHeader);
        new ArrayList<>(request.cookies().keySet()).forEach(request::removeCookie);
    }
}
