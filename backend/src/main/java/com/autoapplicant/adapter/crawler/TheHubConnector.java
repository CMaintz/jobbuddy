package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class TheHubConnector extends AbstractJobSourceConnector {

    private static final String API_URL = "https://thehub.io/api/v1/jobs?limit=50&page=0";
    private static final String JOBS_URL = "https://thehub.io/jobs";
    private static final String JOB_BASE_URL = "https://thehub.io/jobs/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JobSource getSource() {
        return JobSource.THE_HUB;
    }

    @Override
    public List<RawJobData> fetchJobs(CrawlConfig config) {
        List<RawJobData> results = new ArrayList<>();

        // Try JSON API first
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("User-Agent", USER_AGENT);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body != null && body.trim().startsWith("[")) {
                results = parseJsonApiResponse(body, config);
                log.info("TheHub JSON API crawl complete: {} jobs collected", results.size());
                return results;
            }
        } catch (Exception e) {
            log.warn("TheHub JSON API request failed, falling back to HTML scraping: {}", e.getMessage());
        }

        // Fallback: HTML scraping
        results = scrapeHtmlFallback(config);
        log.info("TheHub HTML scrape complete: {} jobs collected", results.size());
        return results;
    }

    private List<RawJobData> parseJsonApiResponse(String jsonBody, CrawlConfig config) {
        List<RawJobData> results = new ArrayList<>();
        try {
            JsonNode array = objectMapper.readTree(jsonBody);
            if (!array.isArray()) {
                return results;
            }

            for (JsonNode job : array) {
                String id = job.path("_id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }

                String urlField = job.path("url").asText(null);
                String slug = job.path("slug").asText(null);
                String jobUrl = urlField != null && !urlField.isBlank()
                        ? urlField
                        : (slug != null && !slug.isBlank() ? JOB_BASE_URL + slug : JOB_BASE_URL + id);

                String rawJson = job.toString();

                try {
                    Thread.sleep(config.delayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while processing TheHub jobs, returning partial results");
                    break;
                }

                results.add(new RawJobData(
                        JobSource.THE_HUB,
                        id,
                        jobUrl,
                        null,
                        rawJson,
                        Instant.now()
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to parse TheHub JSON API response: {}", e.getMessage());
        }
        return results;
    }

    private List<RawJobData> scrapeHtmlFallback(CrawlConfig config) {
        List<RawJobData> results = new ArrayList<>();

        Document listPage;
        try {
            listPage = Jsoup.connect(JOBS_URL)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .get();
        } catch (Exception e) {
            log.warn("TheHub HTML fallback: failed to fetch job listing page {}: {}", JOBS_URL, e.getMessage());
            return results;
        }

        // Try multiple selectors
        Elements jobElements = new Elements();
        String[] selectors = {".JobCard", ".job-card", "article[data-job-id]"};
        for (String selector : selectors) {
            jobElements = listPage.select(selector);
            if (!jobElements.isEmpty()) {
                log.info("TheHub HTML fallback: using selector '{}', found {} elements", selector, jobElements.size());
                break;
            }
        }

        if (jobElements.isEmpty()) {
            log.warn("TheHub HTML fallback: no job elements found on {}", JOBS_URL);
            return results;
        }

        for (Element jobEl : jobElements) {
            Element link = jobEl.selectFirst("a[href]");
            if (link == null) {
                continue;
            }

            String href = link.absUrl("href");
            if (href.isBlank()) {
                href = link.attr("href");
                if (!href.startsWith("http")) {
                    href = "https://thehub.io" + href;
                }
            }

            String jobId = href.replaceAll(".*/", "");

            String detailHtml = "";
            try {
                Thread.sleep(config.delayMs());
                Document detailDoc = Jsoup.connect(href)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .get();
                detailHtml = detailDoc.outerHtml();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while fetching TheHub detail pages, returning partial results");
                break;
            } catch (Exception e) {
                log.warn("TheHub HTML fallback: failed to fetch detail page {}: {}", href, e.getMessage());
                detailHtml = jobEl.outerHtml();
            }

            results.add(new RawJobData(
                    JobSource.THE_HUB,
                    jobId,
                    href,
                    detailHtml,
                    null,
                    Instant.now()
            ));
        }

        return results;
    }
}
