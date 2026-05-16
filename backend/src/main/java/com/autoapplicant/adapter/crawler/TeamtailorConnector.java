package com.autoapplicant.adapter.crawler;

import com.autoapplicant.config.AppProperties;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fetches job listings from Teamtailor career pages.
 *
 * Strategy: two-phase JSON-LD scrape (same as TheHubConnector).
 *  1. Fetch the jobs listing page for each configured career page URL.
 *     Extract links to individual job detail pages.
 *  2. Fetch each detail page and extract schema.org/JobPosting JSON-LD.
 *
 * Career page URLs are configured via app.teamtailor.career-page-urls.
 * Common Teamtailor URL formats:
 *   https://{company}.teamtailor-jobs.com
 *   https://jobs.{company}.com  (custom domain — add full URL)
 */
@Component
public class TeamtailorConnector extends AbstractJobSourceConnector {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";
    private static final int    CONNECT_TIMEOUT_MS = 20_000;

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    public TeamtailorConnector(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public JobSource getSource() {
        return JobSource.TEAMTAILOR;
    }

    @Override
    public List<RawJobData> fetchJobs(CrawlConfig config) {
        List<String> careerPageUrls = appProperties.getTeamtailor().getCareerPageUrls();
        if (careerPageUrls.isEmpty()) {
            log.info("Teamtailor: no career page URLs configured (app.teamtailor.career-page-urls) — skipping");
            return List.of();
        }

        List<RawJobData> results = new ArrayList<>();
        int targetJobs = config.maxPages() * 15;

        for (String baseUrl : careerPageUrls) {
            if (results.size() >= targetJobs) break;
            try {
                Set<String> jobUrls = collectJobUrls(baseUrl, config);
                log.info("Teamtailor: {} — found {} job URLs", baseUrl, jobUrls.size());

                for (String jobUrl : jobUrls) {
                    if (results.size() >= targetJobs) break;
                    try {
                        Thread.sleep(config.delayMs());
                        Document doc = Jsoup.connect(jobUrl)
                                .userAgent(USER_AGENT)
                                .timeout(CONNECT_TIMEOUT_MS)
                                .get();

                        String jsonLd   = extractJsonLd(doc);
                        String content  = jsonLd != null
                                ? buildContentFromJsonLd(jsonLd, jobUrl)
                                : doc.outerHtml();
                        String jobId    = extractId(baseUrl, jobUrl);

                        results.add(new RawJobData(
                                JobSource.TEAMTAILOR,
                                jobId,
                                jobUrl,
                                content,
                                jsonLd,
                                Instant.now()
                        ));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return results;
                    } catch (Exception e) {
                        log.warn("Teamtailor: failed to fetch detail page {}: {}", jobUrl, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Teamtailor: failed to collect URLs from {}: {}", baseUrl, e.getMessage());
            }
        }

        log.info("Teamtailor crawl complete: {} jobs collected", results.size());
        return results;
    }

    // ── Collect job URLs from the listing page ────────────────────────────────

    private Set<String> collectJobUrls(String baseUrl, CrawlConfig config) {
        Set<String> urls = new LinkedHashSet<>();
        String normalBase = baseUrl.replaceAll("/$", "");

        for (int page = 1; page <= config.maxPages(); page++) {
            // Teamtailor listing pages are typically paginated via ?page=N
            String listUrl = page == 1 ? normalBase + "/jobs" : normalBase + "/jobs?page=" + page;
            try {
                Document doc = Jsoup.connect(listUrl)
                        .userAgent(USER_AGENT)
                        .timeout(CONNECT_TIMEOUT_MS)
                        .get();

                Set<String> found = extractJobLinks(doc, normalBase);
                if (found.isEmpty()) {
                    log.debug("Teamtailor: no links found on page {} for {}", page, baseUrl);
                    break;
                }
                urls.addAll(found);
                Thread.sleep(config.delayMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Many Teamtailor sites don't have a /jobs sub-path — try root listing
                if (page == 1) {
                    try {
                        Document doc = Jsoup.connect(normalBase)
                                .userAgent(USER_AGENT)
                                .timeout(CONNECT_TIMEOUT_MS)
                                .get();
                        urls.addAll(extractJobLinks(doc, normalBase));
                    } catch (Exception ex) {
                        log.warn("Teamtailor: failed to load listing for {}: {}", baseUrl, ex.getMessage());
                    }
                }
                break;
            }
        }
        return urls;
    }

    private Set<String> extractJobLinks(Document doc, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("abs:href");  // Jsoup resolves relative URLs
            if (href.isBlank()) {
                href = link.attr("href");
                if (href.startsWith("/")) href = baseUrl + href;
            }
            // Keep only links that look like individual job postings (contain /jobs/ with a slug)
            if (href.startsWith(baseUrl) && href.contains("/jobs/") && href.length() > baseUrl.length() + "/jobs/".length()) {
                int q = href.indexOf('?');
                urls.add(q > 0 ? href.substring(0, q) : href);
            }
        }
        return urls;
    }

    // ── JSON-LD extraction (shared logic with TheHubConnector) ────────────────

    private String extractJsonLd(Document doc) {
        Elements scripts = doc.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.html().trim();
            try {
                JsonNode node = objectMapper.readTree(json);
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
            } catch (Exception ignored) {}
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

    private String buildContentFromJsonLd(String jsonLd, String jobUrl) {
        try {
            JsonNode job = objectMapper.readTree(jsonLd);
            StringBuilder sb = new StringBuilder();
            appendField(sb, "Title",           job, "title");
            appendField(sb, "Description",     job, "description");
            appendField(sb, "Employment Type", job, "employmentType");
            appendField(sb, "Date Posted",     job, "datePosted");
            appendField(sb, "URL",             job, "url");
            if (!sb.toString().contains("URL:")) sb.append("URL: ").append(jobUrl).append('\n');

            JsonNode org = job.path("hiringOrganization");
            if (!org.isMissingNode()) {
                String name = org.path("name").asText(null);
                if (name != null) sb.append("Company: ").append(name).append('\n');
            }
            JsonNode loc = job.path("jobLocation");
            if (!loc.isMissingNode()) {
                JsonNode addr = loc.path("address");
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
            return sb.length() > 0 ? sb.toString() : jsonLd;
        } catch (Exception e) {
            return jsonLd;
        }
    }

    private void appendField(StringBuilder sb, String label, JsonNode node, String key) {
        JsonNode val = node.path(key);
        if (!val.isMissingNode() && !val.isNull()) {
            String text = val.isTextual()
                    ? (key.equals("description") ? Jsoup.parse(val.asText()).text() : val.asText())
                    : val.toString();
            if (!text.isBlank()) sb.append(label).append(": ").append(text).append('\n');
        }
    }

    private String extractId(String baseUrl, String jobUrl) {
        // Strip the base URL prefix, then take remaining path as ID
        String suffix = jobUrl.startsWith(baseUrl) ? jobUrl.substring(baseUrl.length()) : jobUrl;
        suffix = suffix.replaceAll("^/+", "").replaceAll("/+$", "").replace('/', '-');
        return suffix.isBlank() ? jobUrl : suffix;
    }
}
