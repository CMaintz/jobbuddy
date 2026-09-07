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

    protected String fetchWithRetry(String url, int maxRetries) {
        Exception last = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (attempt > 0) Thread.sleep(1000L * attempt);
                return restTemplate.getForObject(url, String.class);
            } catch (Exception e) {
                last = e;
                log.warn("Fetch attempt {} failed for {}: {}", attempt + 1, url, e.getMessage());
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
