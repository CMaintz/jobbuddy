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
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches job ads from Jobnet.dk — the Danish public employment portal.
 *
 * Jobnet exposes an unauthenticated JSON BFF:
 *   search: /bff/FindJob/Search?resultsPerPage=N&pageNumber=M&orderType=PublicationDate
 *   detail: /bff/FindJob/JobAdDetails/{id}?incrementViews=false
 *
 * The detail response carries a structured application deadline, which we pass
 * through so the ingestion pipeline can store it without AI extraction.
 */
@Component
public class JobnetConnector extends AbstractJobSourceConnector {

    private static final String SEARCH_URL =
            "https://jobnet.dk/bff/FindJob/Search?resultsPerPage=%d&pageNumber=%d&orderType=PublicationDate";
    private static final String DETAIL_URL =
            "https://jobnet.dk/bff/FindJob/JobAdDetails/%s?incrementViews=false";
    private static final String PUBLIC_URL =
            "https://job.jobnet.dk/CV/FindWork/Details/%s";

    private static final int PER_PAGE = 100;
    /** Incremental mode stops after this many consecutive all-known pages. */
    private static final int CONSECUTIVE_KNOWN_THRESHOLD = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JobSource getSource() {
        return JobSource.JOBNET;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        int totalCount = 0;
        int consecutiveKnownPages = 0;

        for (int page = 1; page <= config.maxPages(); page++) {
            JsonNode jobAds;
            try {
                String json = fetchWithRetry(String.format(SEARCH_URL, PER_PAGE, page), 5);
                jobAds = objectMapper.readTree(json).path("jobAds");
            } catch (Exception e) {
                log.warn("Jobnet: search page {} failed: {}", page, e.getMessage());
                break;
            }

            if (!jobAds.isArray() || jobAds.isEmpty()) {
                log.info("Jobnet: empty page {} — feed exhausted", page);
                break;
            }

            int newOnPage = 0;
            for (JsonNode ad : jobAds) {
                String id = ad.path("jobAdId").asText(null);
                if (id == null || id.isBlank()) continue;
                if (config.isKnownGuid().test(id)) continue;

                try {
                    Thread.sleep(config.delayMs());
                    ingestDetail(id, ad, config);
                    newOnPage++;
                    totalCount++;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Jobnet: interrupted, stopping after {} jobs", totalCount);
                    return;
                } catch (Exception e) {
                    log.warn("Jobnet: failed to ingest ad {}: {}", id, e.getMessage());
                }
            }

            log.info("Jobnet: page {} — {} new jobs ({} total)", page, newOnPage, totalCount);

            // Ordered by publication date, so consecutive all-known pages mean we're caught up.
            if (!config.force()) {
                if (newOnPage == 0) {
                    if (++consecutiveKnownPages >= CONSECUTIVE_KNOWN_THRESHOLD) {
                        log.info("Jobnet: caught up — stopping");
                        break;
                    }
                } else {
                    consecutiveKnownPages = 0;
                }
            }
        }

        log.info("Jobnet crawl complete: {} jobs collected", totalCount);
    }

    private void ingestDetail(String id, JsonNode searchAd, CrawlConfig config) throws Exception {
        String detailJson = fetchWithRetry(String.format(DETAIL_URL, id), 3);
        JsonNode detail = objectMapper.readTree(detailJson);

        String title = detail.path("title").asText(searchAd.path("title").asText(""));
        String employer = detail.path("employer").path("name")
                .asText(searchAd.path("hiringOrgName").asText(""));
        String city = detail.path("job").path("address").path("city")
                .asText(searchAd.path("municipality").asText(""));
        String body = detail.path("body").asText("");

        LocalDate deadline = parseDeadline(
                detail.path("application").path("deadlineDate").asText(null),
                searchAd.path("applicationDeadline").asText(null));

        // Minimal HTML wrapper so TextCleaningService/enrichment treat it like any crawled page.
        StringBuilder html = new StringBuilder("<html><head><title>")
                .append(escape(title)).append("</title></head><body><h1>")
                .append(escape(title)).append("</h1><p>")
                .append(escape(employer));
        if (!city.isBlank()) html.append(" — ").append(escape(city));
        if (deadline != null) html.append(" — Ansøgningsfrist: ").append(deadline);
        html.append("</p>").append(body).append("</body></html>");

        List<String> categories = new ArrayList<>();
        String occupation = searchAd.path("occupation").asText(null);
        if (occupation != null && !occupation.isBlank()) categories.add(occupation);
        String label = detail.path("job").path("preferredLabelDa").asText(null);
        if (label != null && !label.isBlank() && !categories.contains(label)) categories.add(label);

        config.onJobFound().accept(new RawJobData(
                JobSource.JOBNET, id, String.format(PUBLIC_URL, id),
                html.toString(), detailJson, Instant.now(),
                categories, shortDescription(body), deadline,
                employer.isBlank() ? null : employer, null));
    }

    /** Detail deadline wins over the search-card one; both are ISO date or datetime strings. */
    private static LocalDate parseDeadline(String... candidates) {
        for (String raw : candidates) {
            if (raw == null || raw.length() < 10) continue;
            try {
                return LocalDate.parse(raw.substring(0, 10));
            } catch (Exception ignored) {
                // fall through to the next candidate
            }
        }
        return null;
    }

    private static String shortDescription(String bodyHtml) {
        if (bodyHtml == null || bodyHtml.isBlank()) return null;
        String text = Jsoup.parse(bodyHtml).text().trim();
        if (text.isBlank()) return null;
        return text.length() > 400 ? text.substring(0, 400) + "…" : text;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
