package com.autoapplicant.adapter.web;

import com.autoapplicant.adapter.crawler.UrlSafetyValidator;
import com.autoapplicant.port.out.web.WebPageFetchPort;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Fetches a public web page and returns its visible text, stripped of markup. SSRF-guarded via
 * {@link UrlSafetyValidator} — a URL resolving to internal infrastructure is never fetched.
 * Output is capped so a huge page can't blow up a downstream prompt.
 */
@Component
public class HttpWebPageFetchAdapter implements WebPageFetchPort {

    private static final Logger log = LoggerFactory.getLogger(HttpWebPageFetchAdapter.class);
    private static final int MAX_CHARS = 6000;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public Optional<String> fetchText(String url) {
        if (!UrlSafetyValidator.isSafeHttpUrl(url)) {
            log.debug("Web fetch refused unsafe/non-public URL: {}", url);
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return Optional.empty();
            String text = Jsoup.parse(response.body()).text();
            if (text.isBlank()) return Optional.empty();
            return Optional.of(text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text);
        } catch (Exception e) {
            log.debug("Web fetch failed for {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
