package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

/** Every redirect hop is SSRF-checked; IP literals keep the validator offline. */
class SafeRedirectFetcherTest {

    private static final String PUBLIC_URL = "http://93.184.216.34/jobs/1";
    private static final int FOUND = 302;
    private static final int OK = 200;
    private static final String OTHER_HOST_URL = "http://8.8.8.8/elsewhere";
    private static final String AUTHORIZATION = "Authorization";
    private static final String COOKIE = "Cookie";
    private static final String ACCEPT = "Accept";
    private static final String TOKEN = "Bearer secret";
    private static final String SESSION = "sid=1";
    private static final String HTML = "text/html";

    private final List<URI> sent = new ArrayList<>();
    private final List<HttpRequest> sentRequests = new ArrayList<>();

    @Test
    void refusesRedirectToLoopback() throws Exception {
        SafeRedirectFetcher fetcher = fetcherAnswering(uri -> redirect("http://127.0.0.1:8080/admin"));

        Optional<HttpResponse<String>> result = fetcher.send(get(PUBLIC_URL));

        assertThat(result).isEmpty();
        assertThat(sent).containsExactly(URI.create(PUBLIC_URL));
    }

    @Test
    void refusesRedirectToCloudMetadataEndpoint() throws Exception {
        SafeRedirectFetcher fetcher =
                fetcherAnswering(uri -> redirect("http://169.254.169.254/latest/meta-data/"));

        assertThat(fetcher.send(get(PUBLIC_URL))).isEmpty();
        assertThat(sent).hasSize(1);
    }

    @Test
    void refusesUnsafeFirstUrlWithoutSending() throws Exception {
        SafeRedirectFetcher fetcher = fetcherAnswering(uri -> ok());

        assertThat(fetcher.send(get("http://10.0.0.5/x"))).isEmpty();
        assertThat(sent).isEmpty();
    }

    @Test
    void followsRelativeRedirectOnTheSamePublicHost() throws Exception {
        SafeRedirectFetcher fetcher = fetcherAnswering(
                uri -> uri.getPath().equals("/jobs/1") ? redirect("/jobs/1/view") : ok());

        Optional<HttpResponse<String>> result = fetcher.send(get(PUBLIC_URL));

        assertThat(result).isPresent();
        assertThat(result.get().statusCode()).isEqualTo(OK);
        assertThat(sent).containsExactly(
                URI.create(PUBLIC_URL), URI.create("http://93.184.216.34/jobs/1/view"));
    }

    @Test
    void givesUpAfterTheHopCap() throws Exception {
        SafeRedirectFetcher fetcher = fetcherAnswering(uri -> redirect(PUBLIC_URL));

        assertThat(fetcher.send(get(PUBLIC_URL))).isEmpty();
        assertThat(sent).hasSize(RedirectHops.MAX_REDIRECT_HOPS + 1);
    }

    @Test
    void redirectWithoutLocationIsAFailureNotTheFinalResponse() throws Exception {
        SafeRedirectFetcher fetcher = fetcherAnswering(uri -> response(FOUND, Map.of()));

        assertThat(fetcher.send(get(PUBLIC_URL))).isEmpty();
    }

    @Test
    void redirectWithMalformedLocationIsAFailureNotTheFinalResponse() throws Exception {
        SafeRedirectFetcher fetcher = fetcherAnswering(uri -> redirect("http://[::1"));

        assertThat(fetcher.send(get(PUBLIC_URL))).isEmpty();
        assertThat(sent).hasSize(1);
    }

    @Test
    void crossOriginHopDropsCredentialHeadersButKeepsTheRest() throws Exception {
        SafeRedirectFetcher fetcher = fetcherAnswering(
                uri -> uri.toString().equals(PUBLIC_URL) ? redirect(OTHER_HOST_URL) : ok());

        fetcher.send(withCredentials(PUBLIC_URL));

        HttpRequest hop = sentRequests.get(1);
        assertThat(hop.uri()).isEqualTo(URI.create(OTHER_HOST_URL));
        assertThat(hop.headers().firstValue(AUTHORIZATION)).isEmpty();
        assertThat(hop.headers().firstValue(COOKIE)).isEmpty();
        assertThat(hop.headers().firstValue(ACCEPT)).contains(HTML);
    }

    @Test
    void sameOriginHopKeepsCredentialHeaders() throws Exception {
        SafeRedirectFetcher fetcher = fetcherAnswering(
                uri -> uri.getPath().equals("/jobs/1") ? redirect("/jobs/1/view") : ok());

        fetcher.send(withCredentials(PUBLIC_URL));

        HttpRequest hop = sentRequests.get(1);
        assertThat(hop.headers().firstValue(AUTHORIZATION)).contains(TOKEN);
        assertThat(hop.headers().firstValue(COOKIE)).contains(SESSION);
    }

    private static HttpRequest withCredentials(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header(AUTHORIZATION, TOKEN)
                .header(COOKIE, SESSION)
                .header(ACCEPT, HTML)
                .GET()
                .build();
    }

    private SafeRedirectFetcher fetcherAnswering(Function<URI, HttpResponse<String>> responder)
            throws Exception {
        HttpClient client = mock(HttpClient.class);
        doAnswer(invocation -> {
            URI uri = invocation.<HttpRequest>getArgument(0).uri();
            sent.add(uri);
            sentRequests.add(invocation.getArgument(0));
            return responder.apply(uri);
        }).when(client).send(any(HttpRequest.class), any());
        return new SafeRedirectFetcher(client);
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).GET().build();
    }

    private static HttpResponse<String> redirect(String location) {
        return response(FOUND, Map.of("Location", List.of(location)));
    }

    private static HttpResponse<String> ok() {
        return response(OK, Map.of());
    }

    private static HttpResponse<String> response(int status, Map<String, List<String>> headers) {
        return new StubResponse(status, HttpHeaders.of(headers, (name, value) -> true));
    }

    private record StubResponse(int statusCode, HttpHeaders headers) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public String body() {
            return "";
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return null;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
