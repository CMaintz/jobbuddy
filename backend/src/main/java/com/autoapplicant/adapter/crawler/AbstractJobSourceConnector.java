package com.autoapplicant.adapter.crawler;

import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

public abstract class AbstractJobSourceConnector implements JobSourceConnectorPort {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final RestTemplate restTemplate = new RestTemplate();

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
}
