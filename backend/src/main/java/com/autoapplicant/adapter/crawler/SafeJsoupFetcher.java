package com.autoapplicant.adapter.crawler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
            new SafeJsoupFetcher(UrlSafetyValidator::isSafeHttpUrl, Jsoup::connect);

    private static final int FIRST_ERROR_STATUS = 400;

    private final Predicate<String> guard;
    private final Function<String, Connection> connector;

    SafeJsoupFetcher(Predicate<String> guard, Function<String, Connection> connector) {
        this.guard = guard;
        this.connector = connector;
    }

    /**
     * Fetches {@code url}, applying {@code configure} (user agent, headers, timeout) to the
     * connection of every hop. HTTP error statuses come back as responses, not exceptions, with
     * the body already read so the connection is released.
     * Empty when a hop is unsafe, a redirect has no usable Location, or the hop cap is exceeded.
     */
    public Optional<Connection.Response> fetch(String url, UnaryOperator<Connection> configure)
            throws IOException {
        Optional<URI> start = RedirectHops.parse(url);
        if (start.isEmpty()) return Optional.empty();
        return new Chain(start.get(), configure).fetch(start.get(), Map.of(), 0);
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

    /** One redirect chain, started at {@code origin}. */
    private final class Chain {
        private final URI origin;
        private final UnaryOperator<Connection> configure;

        Chain(URI origin, UnaryOperator<Connection> configure) {
            this.origin = origin;
            this.configure = configure;
        }

        /** {@code cookies} were set earlier in the chain by {@code target}'s own origin. */
        Optional<Connection.Response> fetch(URI target, Map<String, String> cookies, int hop)
                throws IOException {
            if (!RedirectHops.mayFetch(guard, target, hop)) return Optional.empty();
            Connection.Response response = connect(target, cookies).execute();
            if (!RedirectHops.isRedirect(response.statusCode())) return Optional.of(settled(response));
            discardBody(response);
            Optional<URI> next = RedirectHops.target(
                    target, Optional.ofNullable(response.header(RedirectHops.LOCATION_HEADER)));
            if (next.isEmpty()) return Optional.empty();
            return fetch(next.get(), cookiesFor(next.get(), target, cookies, response), hop + 1);
        }

        /** A connection for one hop; credentials the caller set are dropped off the origin. */
        private Connection connect(URI target, Map<String, String> cookies) {
            Connection connection = configure.apply(connector.apply(target.toString()))
                    .followRedirects(false)
                    .ignoreHttpErrors(true);
            if (!RedirectHops.sameOrigin(origin, target)) dropCredentials(connection.request());
            return connection.cookies(cookies);
        }
    }

    /**
     * The cookies the next hop sends: whatever this hop's origin has set so far, when the next
     * hop stays on it. A login or consent bounce (302 + Set-Cookie back to itself) depends on
     * this, and would otherwise loop until the hop cap.
     */
    private static Map<String, String> cookiesFor(
            URI next, URI current, Map<String, String> carried, Connection.Response response) {
        if (!RedirectHops.sameOrigin(current, next)) return Map.of();
        Map<String, String> cookies = new HashMap<>(carried);
        cookies.putAll(response.cookies());
        return cookies;
    }

    /** An error body is read up front so its connection is released even if nobody reads it. */
    private static Connection.Response settled(Connection.Response response) {
        return response.statusCode() >= FIRST_ERROR_STATUS ? response.bufferUp() : response;
    }

    /** Closes a redirect's unread body, which releases its connection. */
    private static void discardBody(Connection.Response response) {
        try {
            response.bodyStream().close();
        } catch (IOException | UncheckedIOException e) {
            log.debug("Could not close redirect body: {}", e.getMessage());
        }
    }

    private static void dropCredentials(Connection.Request request) {
        RedirectHops.CREDENTIAL_HEADERS.forEach(request::removeHeader);
        new ArrayList<>(request.cookies().keySet()).forEach(request::removeCookie);
    }
}
