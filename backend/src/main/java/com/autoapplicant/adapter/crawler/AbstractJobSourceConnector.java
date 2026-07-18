package com.autoapplicant.adapter.crawler;

import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

public abstract class AbstractJobSourceConnector implements JobSourceConnectorPort {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final RestTemplate restTemplate = new RestTemplate();

    protected static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";

    @Override
    public void fetchJobs(CrawlConfig config) {
        log.warn("Connector for {} not yet implemented — skipping", getSource());
    }

    /**
     * GET with exponential backoff + jitter, capped at 5s between attempts.
     * Retries only transient failures (429, 5xx, I/O errors); other 4xx are
     * permanent and rethrown immediately.
     */
    protected String fetchWithRetry(String url, int maxRetries) {
        Exception last = null;
        long delay = 500;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(delay + (long) (Math.random() * 500));
                    delay = Math.min(delay * 2, 5_000);
                }
                return restTemplate.getForObject(url, String.class);
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status != 429 && status < 500) {
                    throw new RuntimeException("Non-retryable HTTP " + status + " for " + url, e);
                }
                last = e;
                log.warn("Fetch attempt {} got HTTP {} for {}", attempt + 1, status, url);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted fetching " + url, ie);
            } catch (Exception e) {
                last = e;
                log.warn("Fetch attempt {} failed for {}: {}", attempt + 1, url, e.getMessage());
            }
        }
        throw new RuntimeException("All retries exhausted for " + url, last);
    }

    /** JSON POST with the same backoff semantics as {@link #fetchWithRetry}. */
    protected String postJsonWithRetry(String url, String jsonBody, int maxRetries) {
        Exception last = null;
        long delay = 500;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(delay + (long) (Math.random() * 500));
                    delay = Math.min(delay * 2, 5_000);
                }
                var headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                return restTemplate.postForObject(url,
                        new org.springframework.http.HttpEntity<>(jsonBody, headers), String.class);
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status != 429 && status < 500) {
                    throw new RuntimeException("Non-retryable HTTP " + status + " for " + url, e);
                }
                last = e;
                log.warn("POST attempt {} got HTTP {} for {}", attempt + 1, status, url);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted posting to " + url, ie);
            } catch (Exception e) {
                last = e;
                log.warn("POST attempt {} failed for {}: {}", attempt + 1, url, e.getMessage());
            }
        }
        throw new RuntimeException("All retries exhausted for " + url, last);
    }

    protected Document parseHtml(String html) {
        return Jsoup.parse(html);
    }

    /**
     * Extracts the job URL from an RSS {@code <item>} element.
     * JSoup's XML parser treats {@code <link>} as self-closing so the URL often
     * lands as a sibling text node. Falls back to {@code <guid>} which is always
     * a permalink URL on Jobindex and IT-Jobbank feeds.
     */
    protected String extractRssLink(Element item) {
        String link = item.select("link").text().trim();
        if (!link.isBlank()) return link;

        Element linkEl = item.select("link").first();
        if (linkEl != null) {
            link = linkEl.html().trim();
            if (!link.isBlank()) return link;
        }

        String guid = item.select("guid").text().trim();
        if (!guid.isBlank() && guid.startsWith("http")) return guid;

        return "";
    }
}
