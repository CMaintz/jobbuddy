package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Fetches job listings from Jobdanmark.dk.
 *
 * Search is a JSON POST: /api/jobsearch/search/{page} with an (empty) filter
 * body returns all listings, 30 per page, with structured metadata including
 * the application deadline. Details are regular job pages carrying a JSON-LD
 * JobPosting block. (Approach ported from ai-job-search-master's jobdanmark CLI.)
 */
@Component
public class JobdanmarkConnector extends AbstractJobSourceConnector {

    private static final String BASE_URL = "https://jobdanmark.dk";
    private static final String SEARCH_URL = BASE_URL + "/api/jobsearch/search/%d";
    private static final String SEARCH_BODY =
            "{\"jobTypes\":[],\"filters\":[],\"locationMode\":\"Text\",\"distance\":50}";

    /**
     * The search API mixes boosted listings into the ordering, so a known page
     * doesn't strictly mean everything deeper is known — use a slightly higher
     * threshold than the date-ordered feeds.
     */
    private static final int CONSECUTIVE_KNOWN_THRESHOLD = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JobSource getSource() {
        return JobSource.JOBDANMARK;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        int totalCount = 0;
        int consecutiveKnownPages = 0;

        for (int page = 1; page <= config.maxPages(); page++) {
            JsonNode items;
            int totalPages;
            try {
                JsonNode root = objectMapper.readTree(
                        postJsonWithRetry(String.format(SEARCH_URL, page), SEARCH_BODY, 5));
                items = root.path("items");
                totalPages = root.path("totalPages").asInt(page);
            } catch (Exception e) {
                log.warn("Jobdanmark: search page {} failed: {}", page, e.getMessage());
                break;
            }

            if (!items.isArray() || items.isEmpty()) {
                log.info("Jobdanmark: empty page {} — done", page);
                break;
            }

            int newOnPage = 0;
            for (JsonNode item : items) {
                String relativeUrl = item.path("url").asText("");
                String slug = relativeUrl.replaceFirst("^/job/", "");
                if (slug.isBlank()) continue;
                if (config.isKnownGuid().test(slug)) continue;

                try {
                    Thread.sleep(config.delayMs());
                    ingestItem(slug, relativeUrl, item, config);
                    newOnPage++;
                    totalCount++;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Jobdanmark: interrupted, stopping after {} jobs", totalCount);
                    return;
                } catch (Exception e) {
                    log.warn("Jobdanmark: failed to ingest '{}': {}", slug, e.getMessage());
                }
            }

            log.info("Jobdanmark: page {}/{} — {} new jobs ({} total)", page, totalPages, newOnPage, totalCount);

            if (page >= totalPages) break;
            if (!config.force()) {
                if (newOnPage == 0) {
                    if (++consecutiveKnownPages >= CONSECUTIVE_KNOWN_THRESHOLD) {
                        log.info("Jobdanmark: caught up — stopping");
                        break;
                    }
                } else {
                    consecutiveKnownPages = 0;
                }
            }
        }

        log.info("Jobdanmark crawl complete: {} jobs collected", totalCount);
    }

    private void ingestItem(String slug, String relativeUrl, JsonNode item, CrawlConfig config) {
        String url = relativeUrl.startsWith("http") ? relativeUrl : BASE_URL + relativeUrl;

        // Full page HTML — TextCleaningService/enrichment consume it as usual.
        String detailHtml;
        try {
            detailHtml = Jsoup.connect(url).userAgent(USER_AGENT).timeout(15000).get().outerHtml();
        } catch (Exception e) {
            log.warn("Jobdanmark: detail fetch failed for {} — using search metadata only", url);
            detailHtml = "<html><body><h1>" + item.path("title").asText("") + "</h1><p>"
                    + item.path("companyName").asText("") + " — " + item.path("companyAddress").asText("")
                    + "</p></body></html>";
        }

        String companyName = item.path("companyName").asText(null);
        String address = item.path("companyAddress").asText(null);

        config.onJobFound().accept(new RawJobData(
                JobSource.JOBDANMARK, slug, url, detailHtml, item.toString(), Instant.now(),
                jobTypes(item), null,
                parseDate(item.path("applicationDeadline").asText(null)),
                companyName, null,
                address,
                parseInstant(item.path("publishedDate").asText(null))));
    }

    private static List<String> jobTypes(JsonNode item) {
        List<String> types = new java.util.ArrayList<>();
        item.path("jobTypes").forEach(t -> {
            String v = t.asText(null);
            if (v != null && !v.isBlank()) types.add(v);
        });
        return types;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.length() < 10) return null;
        try { return LocalDate.parse(raw.substring(0, 10)); } catch (Exception e) { return null; }
    }

    private static Instant parseInstant(String raw) {
        LocalDate date = parseDate(raw);
        return date != null ? date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : null;
    }
}
