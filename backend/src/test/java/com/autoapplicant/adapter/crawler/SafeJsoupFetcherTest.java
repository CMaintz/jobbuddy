package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jsoup.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real Jsoup against loopback servers. The guard stands in for {@link UrlSafetyValidator} (which
 * would refuse loopback outright) and treats any path under /internal as the private host.
 */
class SafeJsoupFetcherTest {

    private static final int OK = 200;
    private static final int FOUND = 302;
    private static final int NOT_FOUND = 404;
    private static final String INTERNAL_PATH = "/internal";
    private static final String AUTHORIZATION = "Authorization";
    private static final String COOKIE = "Cookie";
    private static final String ACCEPT = "Accept";
    private static final String HTML = "text/html";

    private final SafeJsoupFetcher fetcher =
            new SafeJsoupFetcher(url -> !url.contains(INTERNAL_PATH));
    private final List<String> hits = new ArrayList<>();
    private final List<Headers> landingHeaders = new ArrayList<>();
    private HttpServer origin;
    private HttpServer other;

    @BeforeEach
    void startServers() throws IOException {
        origin = server();
        other = server();
        origin.createContext("/", this::route);
        other.createContext("/", this::route);
        origin.start();
        other.start();
    }

    @AfterEach
    void stopServers() {
        origin.stop(0);
        other.stop(0);
    }

    @Test
    void refusesARedirectToABlockedHostWithoutRequestingIt() throws IOException {
        Optional<Connection.Response> result = fetcher.fetch(url(origin, "/to-internal"), html());

        assertThat(result).isEmpty();
        assertThat(hits).containsExactly("/to-internal");
    }

    @Test
    void followsARelativeRedirectToTheFinalPage() throws IOException {
        Optional<Connection.Response> result = fetcher.fetch(url(origin, "/to-landing"), html());

        assertThat(result).isPresent();
        assertThat(result.get().statusCode()).isEqualTo(OK);
        assertThat(hits).containsExactly("/to-landing", "/landing");
    }

    @Test
    void givesUpAfterTheHopCap() throws IOException {
        assertThat(fetcher.fetch(url(origin, "/loop"), html())).isEmpty();
        assertThat(hits).hasSize(RedirectHops.MAX_REDIRECT_HOPS + 1);
    }

    @Test
    void malformedLocationIsAFailure() throws IOException {
        assertThat(fetcher.fetch(url(origin, "/malformed"), html())).isEmpty();
    }

    @Test
    void crossOriginHopDropsCredentialsButKeepsOtherHeaders() throws IOException {
        fetcher.fetch(url(origin, "/to-other"), withCredentials());

        assertThat(landingHeaders).hasSize(1);
        Headers landed = landingHeaders.get(0);
        assertThat(landed.containsKey(AUTHORIZATION)).isFalse();
        assertThat(landed.containsKey(COOKIE)).isFalse();
        assertThat(landed.getFirst(ACCEPT)).isEqualTo(HTML);
    }

    @Test
    void sameOriginHopKeepsCredentials() throws IOException {
        fetcher.fetch(url(origin, "/to-landing"), withCredentials());

        assertThat(landingHeaders.get(0).containsKey(AUTHORIZATION)).isTrue();
        assertThat(landingHeaders.get(0).containsKey(COOKIE)).isTrue();
    }

    @Test
    void fetchDocumentTreatsAnHttpErrorAsNoDocument() throws IOException {
        assertThat(fetcher.fetchDocument(url(origin, "/missing"), html())).isEmpty();
        assertThat(fetcher.fetchDocument(url(origin, "/landing"), html())).isPresent();
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        hits.add(path);
        switch (path) {
            case "/to-internal" -> redirect(exchange, INTERNAL_PATH + "/admin");
            case "/to-landing" -> redirect(exchange, "/landing");
            case "/loop" -> redirect(exchange, "/loop");
            case "/to-other" -> redirect(exchange, url(other, "/landing"));
            case "/malformed" -> redirect(exchange, "ht!tp://%%%");
            case "/landing" -> land(exchange);
            default -> reply(exchange, NOT_FOUND, "");
        }
    }

    private void land(HttpExchange exchange) throws IOException {
        landingHeaders.add(exchange.getRequestHeaders());
        reply(exchange, OK, "<html><body>landed</body></html>");
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        reply(exchange, FOUND, "");
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", HTML);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    }

    private static String url(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static UnaryOperator<Connection> html() {
        return connection -> connection.header(ACCEPT, HTML);
    }

    private static UnaryOperator<Connection> withCredentials() {
        return connection -> connection
                .header(ACCEPT, HTML)
                .header(AUTHORIZATION, "Bearer secret")
                .cookie("sid", "1");
    }
}
