package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches IT job postings from Jobnet.dk (the Danish government employment portal).
 *
 * Primary strategy: the internal JSON API that job.jobnet.dk uses for its own search UI.
 * No authentication required — this is the same endpoint the browser calls on every search.
 * Fallback: HTML scraping of the public search result page.
 */
@Component
public class JobnetConnector extends AbstractJobSourceConnector {

    private static final String BASE_URL = "https://job.jobnet.dk";
    private static final String SEARCH_API =
            BASE_URL + "/CV/FindWork/JobPositionPostings" +
            "?Offset=%d&SortValue=CreationDate&SearchString=it%%20software%%20developer" +
            "&Region=&Abroad=false&NearbyJobs=0&ResultsToShow=%d";
    private static final String DETAIL_URL = BASE_URL + "/CV/FindWork/Details/%s";
    private static final String FALLBACK_URL =
            BASE_URL + "/CV/FindWork?SearchString=it+software+developer&SortValue=CreationDate";

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";
    private static final int PAGE_SIZE = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JobSource getSource() {
        return JobSource.JOBNET;
    }

    @Override
    public List<RawJobData> fetchJobs(CrawlConfig config) {
        List<RawJobData> results = tryJsonApi(config);
        if (!results.isEmpty()) {
            log.info("Jobnet JSON API crawl complete: {} jobs collected", results.size());
            return results;
        }
        results = scrapeHtmlFallback(config);
        log.info("Jobnet HTML fallback crawl complete: {} jobs collected", results.size());
        return results;
    }

    private List<RawJobData> tryJsonApi(CrawlConfig config) {
        List<RawJobData> results = new ArrayList<>();
        int maxJobs = config.maxPages() * PAGE_SIZE;

        for (int offset = 0; offset < maxJobs; offset += PAGE_SIZE) {
            String url = String.format(SEARCH_API, offset, PAGE_SIZE);
            String responseBody;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/json");
                headers.set("User-Agent", USER_AGENT);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                responseBody = response.getBody();
            } catch (Exception e) {
                log.warn("Jobnet JSON API request failed at offset {}: {}", offset, e.getMessage());
                break;
            }

            if (responseBody == null || responseBody.isBlank()) break;

            List<RawJobData> page = parseApiPage(responseBody, config);
            results.addAll(page);

            // Stop if the page was not full (last page)
            if (page.size() < PAGE_SIZE) break;
        }
        return results;
    }

    private List<RawJobData> parseApiPage(String json, CrawlConfig config) {
        List<RawJobData> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);

            // Response may be a top-level array or wrapped: {"JobPositionPostings": [...]}
            JsonNode items = root.isArray() ? root : root.path("JobPositionPostings");
            if (!items.isArray() || items.isEmpty()) return results;

            for (JsonNode item : items) {
                String jobId = item.path("AutoMatchType").asText(null);
                if (jobId == null || jobId.isBlank()) {
                    jobId = item.path("JobPositionPostingId").asText(null);
                }
                if (jobId == null || jobId.isBlank()) continue;

                String detailHtml = item.toString(); // raw JSON as fallback
                String detailLink = String.format(DETAIL_URL, jobId);

                try {
                    Thread.sleep(config.delayMs());
                    Document detailDoc = Jsoup.connect(detailLink)
                            .userAgent(USER_AGENT)
                            .timeout(15000)
                            .get();
                    detailHtml = detailDoc.outerHtml();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted fetching Jobnet detail pages, returning partial results");
                    return results;
                } catch (Exception e) {
                    log.debug("Could not fetch Jobnet detail page {}: {} — using JSON summary", detailLink, e.getMessage());
                }

                results.add(new RawJobData(
                        JobSource.JOBNET,
                        jobId,
                        detailLink,
                        detailHtml,
                        item.toString(),
                        Instant.now()
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to parse Jobnet JSON API response: {}", e.getMessage());
        }
        return results;
    }

    private List<RawJobData> scrapeHtmlFallback(CrawlConfig config) {
        List<RawJobData> results = new ArrayList<>();

        Document listPage;
        try {
            listPage = Jsoup.connect(FALLBACK_URL)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .get();
        } catch (Exception e) {
            log.warn("Jobnet HTML fallback: failed to load listing page: {}", e.getMessage());
            return results;
        }

        // Try known selectors for the Jobnet search results page
        Elements jobCards = new Elements();
        for (String sel : new String[]{"article.jobad", ".job-ad-list-item", "li[data-job-id]", ".joblist-item"}) {
            jobCards = listPage.select(sel);
            if (!jobCards.isEmpty()) break;
        }

        if (jobCards.isEmpty()) {
            log.warn("Jobnet HTML fallback: no job card elements found on listing page");
            return results;
        }

        for (Element card : jobCards) {
            Element link = card.selectFirst("a[href]");
            if (link == null) continue;

            String href = link.absUrl("href");
            if (href.isBlank()) href = BASE_URL + link.attr("href");
            String jobId = href.replaceAll(".*/", "").replaceAll("\\?.*", "");

            String detailHtml = card.outerHtml();
            try {
                Thread.sleep(config.delayMs());
                Document detailDoc = Jsoup.connect(href)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .get();
                detailHtml = detailDoc.outerHtml();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.debug("Jobnet HTML fallback: could not fetch detail {}: {}", href, e.getMessage());
            }

            results.add(new RawJobData(
                    JobSource.JOBNET,
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
