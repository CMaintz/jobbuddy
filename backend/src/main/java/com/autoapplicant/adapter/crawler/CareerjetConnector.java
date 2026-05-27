package com.autoapplicant.adapter.crawler;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

/**
 * Fetches Danish job listings from the Careerjet public API.
 *
 * Endpoint: https://public.api.careerjet.net/search
 * No auth required beyond a registered affiliate ID (free at careerjet.com/partners/).
 * If no affiliate ID is configured, crawling is skipped with a warning.
 *
 * API docs: https://www.careerjet.com/partners/api/
 */
@Component
public class CareerjetConnector extends AbstractJobSourceConnector {

    private static final String API_BASE = "https://public.api.careerjet.net/search";
    private static final int PAGE_SIZE   = 20;

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    public CareerjetConnector(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public JobSource getSource() {
        return JobSource.CAREERJET;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        String affiliateId = appProperties.getCareerjet().getAffiliateId();
        if (affiliateId == null || affiliateId.isBlank()) {
            log.warn("Careerjet: no affiliate ID configured (app.careerjet.affiliate-id) — skipping crawl");
            return;
        }

        int targetJobs = config.maxPages() * PAGE_SIZE;
        int totalPages = config.maxPages();
        int count = 0;

        for (int page = 1; page <= totalPages && count < targetJobs; page++) {
            String url = UriComponentsBuilder.fromHttpUrl(API_BASE)
                    .queryParam("keywords", "")
                    .queryParam("location", "denmark")
                    .queryParam("affid", affiliateId)
                    .queryParam("locale_code", "en_GB")
                    .queryParam("pagesize", PAGE_SIZE)
                    .queryParam("page", page)
                    .toUriString();

            try {
                Thread.sleep(config.delayMs());
                String json = fetchWithRetry(url, 3);
                JsonNode root = objectMapper.readTree(json);

                if (!"JOBS".equals(root.path("type").asText())) {
                    log.warn("Careerjet: unexpected response type on page {}: {}", page,
                            root.path("type").asText());
                    break;
                }

                JsonNode jobs = root.path("jobs");
                if (!jobs.isArray() || jobs.size() == 0) {
                    log.info("Careerjet: no more jobs on page {}", page);
                    break;
                }

                for (JsonNode job : jobs) {
                    String jobUrl   = job.path("url").asText(null);
                    String title    = job.path("title").asText("");
                    String company  = job.path("company").asText("");
                    String location = job.path("locations").asText("");
                    String desc     = job.path("description").asText("");
                    String date     = job.path("date").asText("");
                    String salary   = job.path("salary").asText("");

                    if (jobUrl == null || jobUrl.isBlank()) continue;

                    String content = buildContent(title, company, location, desc, salary, date, jobUrl);
                    String jobId   = extractId(jobUrl);

                    config.onJobFound().accept(new RawJobData(
                            JobSource.CAREERJET,
                            jobId,
                            jobUrl,
                            content,
                            job.toString(),   // raw JSON as structured payload
                            Instant.now()
                    ));
                    count++;
                }

                log.info("Careerjet: page {} — {} jobs (total: {})", page, jobs.size(), count);

                // Check if we've seen all available pages
                int totalPages_ = root.path("pages").asInt(0);
                if (page >= totalPages_) break;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Careerjet: interrupted, returning partial results ({} so far)", count);
                break;
            } catch (Exception e) {
                log.warn("Careerjet: failed on page {}: {}", page, e.getMessage());
                break;
            }
        }

        log.info("Careerjet crawl complete: {} jobs collected", count);
    }

    private String buildContent(String title, String company, String location,
                                String description, String salary, String date, String url) {
        StringBuilder sb = new StringBuilder();
        if (!title.isBlank())       sb.append("Title: ").append(title).append('\n');
        if (!company.isBlank())     sb.append("Company: ").append(company).append('\n');
        if (!location.isBlank())    sb.append("Location: ").append(location).append('\n');
        if (!description.isBlank()) sb.append("Description: ").append(description).append('\n');
        if (!salary.isBlank())      sb.append("Salary: ").append(salary).append('\n');
        if (!date.isBlank())        sb.append("Date Posted: ").append(date).append('\n');
        sb.append("URL: ").append(url).append('\n');
        return sb.toString();
    }

    private String extractId(String url) {
        // Careerjet job URLs are long redirect URLs; use the last path segment or hash
        int last = url.lastIndexOf('/');
        return last >= 0 && last < url.length() - 1 ? url.substring(last + 1) : url;
    }
}
