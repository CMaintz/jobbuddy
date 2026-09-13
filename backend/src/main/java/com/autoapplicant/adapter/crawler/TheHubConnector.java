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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Crawls The Hub (thehub.io) by:
 *  1. Paginating through the /jobs listing page to collect job-detail URLs
 *  2. Fetching each detail page and extracting the schema.org/JobPosting
 *     JSON-LD block — a structured, stable format explicitly designed for
 *     machine consumption (much more reliable than window.__NUXT__ parsing)
 *  3. Falling back to raw HTML for ingestion if no JSON-LD is found
 */
@Component
public class TheHubConnector extends AbstractJobSourceConnector {

    private static final String JOBS_URL  = "https://thehub.io/jobs";
    private static final String BASE_URL  = "https://thehub.io";

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int MAX_PAGES          = 25;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JobSource getSource() {
        return JobSource.THE_HUB;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        int targetJobs = config.maxPages() * 15;

        // Step 1 — collect job-detail URLs from listing pages
        Set<String> jobUrls = collectJobUrls(config, targetJobs);
        log.info("TheHub: collected {} job URLs to fetch", jobUrls.size());

        if (jobUrls.isEmpty()) {
            log.warn("TheHub: no job URLs found — site structure may have changed");
            return;
        }

        // Step 2 — fetch each detail page and extract JSON-LD
        int count = 0;
        int total = jobUrls.size();
        for (String jobUrl : jobUrls) {
            if (count >= targetJobs) break;
            if (count % 25 == 0) {
                log.info("TheHub: fetching detail pages [{}/{}]...", count, total);
            }
            try {
                Thread.sleep(config.delayMs());
                Document doc = Jsoup.connect(jobUrl)
                        .userAgent(USER_AGENT)
                        .timeout(CONNECT_TIMEOUT_MS)
                        .get();

                String jobId = extractId(jobUrl);
                String content = extractJobContent(doc, jobUrl);

                config.onJobFound().accept(new RawJobData(
                        JobSource.THE_HUB,
                        jobId,
                        jobUrl,
                        content,
                        extractJsonLd(doc),  // raw JSON-LD as structured payload
                        Instant.now(),
                        List.of(),
                        null
                ));
                count++;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("TheHub: interrupted, returning partial results ({} so far)", count);
                break;
            } catch (Exception e) {
                log.warn("TheHub: failed to fetch job page {}: {}", jobUrl, e.getMessage());
            }
        }

        log.info("TheHub crawl complete: {} jobs collected", count);
    }

    // ── Step 1: collect URLs from listing pages ───────────────────────────────

    private Set<String> collectJobUrls(CrawlConfig config, int targetJobs) {
        Set<String> urls = new LinkedHashSet<>();
        int maxPages = Math.min(config.maxPages(), MAX_PAGES);

        for (int page = 1; page <= maxPages && urls.size() < targetJobs; page++) {
            String pageUrl = JOBS_URL + "?page=" + page;
            try {
                Document doc = Jsoup.connect(pageUrl)
                        .userAgent(USER_AGENT)
                        .timeout(CONNECT_TIMEOUT_MS)
                        .get();

                Set<String> found = extractJobLinks(doc);
                if (found.isEmpty()) {
                    log.info("TheHub: no job links on page {}, stopping", page);
                    break;
                }
                urls.addAll(found);
                log.info("TheHub: page {} — {} links (total: {})", page, found.size(), urls.size());

                Thread.sleep(config.delayMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("TheHub: failed to fetch listing page {}: {}", pageUrl, e.getMessage());
                break;
            }
        }
        return urls;
    }

    /**
     * Extracts links to individual job pages.
     * Looks for <a href="/jobs/..."> patterns — these are stable anchor tags
     * in any server-rendered or client-hydrated page.
     */
    private Set<String> extractJobLinks(Document doc) {
        Set<String> urls = new LinkedHashSet<>();
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("href");
            // Job detail pages on TheHub follow /jobs/<slug> — exclude the listing /jobs itself
            if (href.startsWith("/jobs/") && href.length() > "/jobs/".length()) {
                String full = BASE_URL + href;
                // Strip query strings to avoid duplicates from tracking params
                int q = full.indexOf('?');
                urls.add(q > 0 ? full.substring(0, q) : full);
            }
        }
        return urls;
    }

    // ── Step 2: extract content from detail page ──────────────────────────────

    /**
     * Primary: parse schema.org/JobPosting JSON-LD.
     * Fallback: return the page's full HTML for downstream text cleaning.
     */
    private String extractJobContent(Document doc, String jobUrl) {
        String jsonLd = extractJsonLd(doc);
        if (jsonLd != null) {
            return buildContentFromJsonLd(jsonLd, jobUrl);
        }
        // Fallback — ingestion pipeline will clean the HTML
        return doc.outerHtml();
    }

    /**
     * Finds the first <script type="application/ld+json"> block that contains
     * a JobPosting and returns its raw JSON string.
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "Parses untrusted JSON-LD from external sites; objectMapper.readTree "
                    + "throws the checked JsonProcessingException, and the catch must also absorb "
                    + "runtime failures so a single malformed script block skips to the next.")
    private String extractJsonLd(Document doc) {
        Elements scripts = doc.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.html().trim();
            try {
                JsonNode node = objectMapper.readTree(json);
                // Handle both single object and @graph arrays
                if (node.isArray()) {
                    for (JsonNode item : node) {
                        if (isJobPosting(item)) return item.toString();
                    }
                } else if (node.has("@graph")) {
                    for (JsonNode item : node.get("@graph")) {
                        if (isJobPosting(item)) return item.toString();
                    }
                } else if (isJobPosting(node)) {
                    return json;
                }
            } catch (Exception e) {
                // Malformed JSON-LD — try the next script block
            }
        }
        return null;
    }

    private boolean isJobPosting(JsonNode node) {
        if (node == null || !node.isObject()) return false;
        JsonNode type = node.path("@type");
        if (type.isTextual()) return "JobPosting".equals(type.asText());
        if (type.isArray()) {
            for (JsonNode t : type) {
                if ("JobPosting".equals(t.asText())) return true;
            }
        }
        return false;
    }

    /**
     * Converts a schema.org/JobPosting JSON-LD node into readable text
     * for the ingestion pipeline's text cleaning and AI enrichment stages.
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "Parses untrusted JSON-LD from external sites; objectMapper.readTree "
                    + "throws the checked JsonProcessingException, and the catch must also absorb "
                    + "runtime failures (NPE etc.) to fall back to the raw string.")
    private String buildContentFromJsonLd(String jsonLd, String jobUrl) {
        try {
            JsonNode job = objectMapper.readTree(jsonLd);
            StringBuilder sb = new StringBuilder();

            appendText(sb, "Title", job, "title");
            appendOrganization(sb, job.path("hiringOrganization"));
            appendLocation(sb, job.path("jobLocation"));
            appendText(sb, "Description", job, "description");
            appendText(sb, "Employment Type", job, "employmentType");
            appendText(sb, "Date Posted", job, "datePosted");
            appendText(sb, "Valid Through", job, "validThrough");
            appendText(sb, "URL", job, "url");
            if (sb.isEmpty() || sb.indexOf("URL") == -1) {
                sb.append("URL: ").append(jobUrl).append('\n');
            }

            // Salary info
            JsonNode salary = job.path("baseSalary");
            if (!salary.isMissingNode()) {
                JsonNode value = salary.path("value");
                if (value.has("minValue") || value.has("maxValue")) {
                    sb.append("Salary: ");
                    if (value.has("minValue")) sb.append(value.path("minValue").asText());
                    if (value.has("maxValue")) sb.append(" - ").append(value.path("maxValue").asText());
                    String currency = salary.path("currency").asText(null);
                    if (currency != null) sb.append(' ').append(currency);
                    sb.append('\n');
                }
            }

            return sb.length() > 0 ? sb.toString() : jsonLd;
        } catch (Exception e) {
            // Defensive: JSON-LD from external sites can be malformed — this parses
            // untrusted data and must catch both the checked JsonProcessingException
            // and runtime exceptions (NPE etc.). Fall back to the raw string.
            return jsonLd;
        }
    }

    // ── Text extraction helpers ───────────────────────────────────────────────

    private void appendText(StringBuilder sb, String label, JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode val = node.path(key);
            if (!val.isMissingNode() && !val.isNull()) {
                String text = val.isTextual() ? val.asText() : val.toString();
                if (!text.isBlank()) {
                    sb.append(label).append(": ").append(text).append('\n');
                    return;
                }
            }
        }
    }

    private void appendOrganization(StringBuilder sb, JsonNode org) {
        if (org.isMissingNode()) return;
        String name = org.path("name").asText(null);
        if (name != null && !name.isBlank()) sb.append("Company: ").append(name).append('\n');
        String website = org.path("sameAs").asText(null);
        if (website != null && !website.isBlank()) sb.append("Company URL: ").append(website).append('\n');
    }

    private void appendLocation(StringBuilder sb, JsonNode loc) {
        if (loc.isMissingNode()) return;
        JsonNode addr = loc.path("address");
        if (!addr.isMissingNode()) {
            String city    = addr.path("addressLocality").asText(null);
            String country = addr.path("addressCountry").asText(null);
            if (city != null || country != null) {
                sb.append("Location: ");
                if (city != null) sb.append(city);
                if (city != null && country != null) sb.append(", ");
                if (country != null) sb.append(country);
                sb.append('\n');
            }
        }
    }

    private String extractId(String jobUrl) {
        // URL format: https://thehub.io/jobs/<slug>
        int lastSlash = jobUrl.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < jobUrl.length() - 1
                ? jobUrl.substring(lastSlash + 1)
                : jobUrl;
    }
}
