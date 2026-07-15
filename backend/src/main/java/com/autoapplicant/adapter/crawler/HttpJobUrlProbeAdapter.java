package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.UrlProbeOutcome;
import com.autoapplicant.port.out.job.JobUrlProbePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Fetches a posting URL and classifies whether the posting is still live.
 * Only 404/410 count as definitive takedowns; 403/429/999-style responses are
 * bot protection, not evidence the job is gone. A 200 page is additionally
 * scanned for common "this job is no longer available" markers (soft 404s are
 * the norm on job boards — most return 200 with an expired-posting page).
 */
@Component
public class HttpJobUrlProbeAdapter implements JobUrlProbePort {

    private static final Logger log = LoggerFactory.getLogger(HttpJobUrlProbeAdapter.class);

    /** Lower-cased markers seen on Danish and international boards when a posting is closed. */
    private static final List<String> GONE_MARKERS = List.of(
            "job is no longer available",
            "no longer accepting applications",
            "this job has expired",
            "this position has been filled",
            "job posting has expired",
            "this vacancy is closed",
            "annoncen er udløbet",
            "stillingen er besat",
            "jobbet er ikke længere tilgængeligt",
            "opslaget er udløbet",
            "ansøgningsfristen er udløbet"
    );

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public UrlProbeOutcome probe(String url) {
        try {
            HttpResponse<String> response = send(url);

            int status = response.statusCode();
            // One jittered retry on transient statuses so a blip doesn't burn a probe cycle.
            if (status == 429 || status >= 500) {
                Thread.sleep(1_000 + (long) (Math.random() * 1_000));
                response = send(url);
                status = response.statusCode();
            }

            if (status == 404 || status == 410) return UrlProbeOutcome.GONE;
            if (status >= 400) return UrlProbeOutcome.INCONCLUSIVE; // 403/429/451/5xx: blocked or hiccup, not proof

            String body = response.body() == null ? "" : response.body().toLowerCase(Locale.ROOT);
            for (String marker : GONE_MARKERS) {
                if (body.contains(marker)) return UrlProbeOutcome.GONE_SOFT;
            }
            return UrlProbeOutcome.ALIVE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UrlProbeOutcome.INCONCLUSIVE;
        } catch (Exception e) {
            log.debug("URL probe failed for {}: {}", url, e.getMessage());
            return UrlProbeOutcome.INCONCLUSIVE;
        }
    }

    private HttpResponse<String> send(String url) throws java.io.IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "da,en;q=0.8")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
