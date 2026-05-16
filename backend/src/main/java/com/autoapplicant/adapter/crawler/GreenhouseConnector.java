package com.autoapplicant.adapter.crawler;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches job listings from Greenhouse ATS job boards.
 *
 * Endpoint: https://boards-api.greenhouse.io/v1/boards/{company}/jobs?content=true
 * No authentication required — this is a public-facing job board API.
 *
 * Companies are configured via app.greenhouse.companies (list of company board tokens).
 * A board token is the slug in the Greenhouse job board URL, e.g.:
 *   https://boards.greenhouse.io/trustpilot  →  token = "trustpilot"
 */
@Component
public class GreenhouseConnector extends AbstractJobSourceConnector {

    private static final String API_BASE = "https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=true";

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    public GreenhouseConnector(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public JobSource getSource() {
        return JobSource.GREENHOUSE;
    }

    @Override
    public List<RawJobData> fetchJobs(CrawlConfig config) {
        List<String> companies = appProperties.getGreenhouse().getCompanies();
        if (companies.isEmpty()) {
            log.info("Greenhouse: no companies configured (app.greenhouse.companies) — skipping");
            return List.of();
        }

        List<RawJobData> results = new ArrayList<>();
        int targetJobs = config.maxPages() * 20;

        for (String company : companies) {
            if (results.size() >= targetJobs) break;
            String url = String.format(API_BASE, company);
            try {
                Thread.sleep(config.delayMs());
                String json = fetchWithRetry(url, 3);
                JsonNode root = objectMapper.readTree(json);
                JsonNode jobs = root.path("jobs");

                if (!jobs.isArray()) {
                    log.warn("Greenhouse: unexpected response for company '{}' — no jobs array", company);
                    continue;
                }

                int count = 0;
                for (JsonNode job : jobs) {
                    if (results.size() >= targetJobs) break;

                    String jobId    = job.path("id").asText(null);
                    String title    = job.path("title").asText("");
                    String jobUrl   = job.path("absolute_url").asText(null);
                    String location = job.path("location").path("name").asText("");
                    String updated  = job.path("updated_at").asText("");
                    String rawHtml  = job.path("content").asText(""); // HTML job description

                    if (jobId == null || jobUrl == null) continue;

                    // Extract departments for content
                    List<String> depts = new ArrayList<>();
                    JsonNode depsNode = job.path("departments");
                    if (depsNode.isArray()) {
                        for (JsonNode d : depsNode) depts.add(d.path("name").asText(""));
                    }

                    String content = buildContent(title, company, location, rawHtml, depts, updated, jobUrl);

                    results.add(new RawJobData(
                            JobSource.GREENHOUSE,
                            "gh-" + company + "-" + jobId,
                            jobUrl,
                            content,
                            job.toString(),
                            Instant.now()
                    ));
                    count++;
                }
                log.info("Greenhouse: {} — {} jobs collected", company, count);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Greenhouse: interrupted, returning {} results so far", results.size());
                break;
            } catch (Exception e) {
                log.warn("Greenhouse: failed to fetch company '{}': {}", company, e.getMessage());
            }
        }

        log.info("Greenhouse crawl complete: {} jobs from {} companies", results.size(), companies.size());
        return results;
    }

    private String buildContent(String title, String company, String location,
                                String htmlContent, List<String> departments,
                                String updated, String url) {
        StringBuilder sb = new StringBuilder();
        if (!title.isBlank())    sb.append("Title: ").append(title).append('\n');
        sb.append("Company: ").append(company).append('\n');
        if (!location.isBlank()) sb.append("Location: ").append(location).append('\n');
        if (!departments.isEmpty()) {
            sb.append("Department: ").append(String.join(", ", departments)).append('\n');
        }
        if (!updated.isBlank())  sb.append("Updated: ").append(updated).append('\n');
        sb.append("URL: ").append(url).append('\n');
        if (!htmlContent.isBlank()) {
            // Strip HTML tags for clean text ingestion
            sb.append("Description: ").append(Jsoup.parse(htmlContent).text()).append('\n');
        }
        return sb.toString();
    }
}
