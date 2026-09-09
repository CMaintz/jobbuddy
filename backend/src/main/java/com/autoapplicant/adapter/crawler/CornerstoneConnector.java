package com.autoapplicant.adapter.crawler;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches job listings from Cornerstone OnDemand (CSOD) ATS career sites.
 *
 * Strategy (two-phase per tenant):
 *   Phase 1 — Load the career site home page and extract an embedded JWT + API host.
 *   Phase 2 — POST to the CSOD jobs search API using the extracted JWT.
 *
 * Tenants are configured via app.cornerstone-on-demand.tenants (name + siteId).
 * Example career site URL pattern: https://{tenant}.csod.com/ux/ats/careersite/{siteId}/home
 */
@Component
public class CornerstoneConnector extends AbstractJobSourceConnector {

    private static final String HOME_URL_TEMPLATE =
            "https://%s.csod.com/ux/ats/careersite/%d/home?c=%s";
    private static final String JOBS_API_TEMPLATE =
            "%srec-job-search/external/jobs";

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "(?:\"token\"|\"bearerToken\"|\"access_token\")\\s*:\\s*\"(eyJ[^\"]+)\"");
    private static final Pattern API_HOST_PATTERN = Pattern.compile(
            "\"cloud\"\\s*:\\s*\"(https://[^\"]+api\\.csod\\.com/)\"");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CornerstoneConnector(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public JobSource getSource() {
        return JobSource.CORNERSTONE_ONDEMAND;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        List<AppProperties.CornerstoneOnDemand.Tenant> tenants =
                appProperties.getCornerstoneOnDemand().getTenants();
        if (tenants.isEmpty()) {
            log.info("CSOD: no tenants configured (app.cornerstone-on-demand.tenants) — skipping");
            return;
        }

        int count = 0;

        for (AppProperties.CornerstoneOnDemand.Tenant tenant : tenants) {
            try {
                Thread.sleep(config.delayMs());
                List<RawJobData> tenantJobs = crawlTenant(tenant, config);
                for (RawJobData job : tenantJobs) {
                    config.onJobFound().accept(job);
                    count++;
                }
                log.info("CSOD: tenant '{}' — {} jobs collected", tenant.getName(), tenantJobs.size());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("CSOD: interrupted, returning {} results so far", count);
                break;
            } catch (Exception e) {
                log.warn("CSOD: failed to crawl tenant '{}': {}", tenant.getName(), e.getMessage());
            }
        }

        log.info("CSOD crawl complete: {} jobs from {} tenants", count, tenants.size());
    }

    private List<RawJobData> crawlTenant(AppProperties.CornerstoneOnDemand.Tenant tenant,
                                          CrawlConfig config) throws Exception {
        String tenantName = tenant.getName();
        int siteId = tenant.getSiteId();

        // Phase 1: extract JWT and API host from the career site home page
        String homeUrl = String.format(HOME_URL_TEMPLATE, tenantName, siteId, tenantName);
        log.info("CSOD [{}]: fetching home page to extract JWT — {}", tenantName, homeUrl);

        String pageHtml = Jsoup.connect(homeUrl)
                .userAgent(USER_AGENT)
                .timeout(20000)
                .execute()
                .body();

        String jwt = extractGroup(JWT_PATTERN, pageHtml);
        if (jwt == null) {
            log.warn("CSOD [{}]: could not extract JWT from home page — skipping tenant", tenantName);
            return List.of();
        }

        String apiHost = extractGroup(API_HOST_PATTERN, pageHtml);
        if (apiHost == null) {
            // Fallback to the global API host if the tenant-specific one isn't embedded
            apiHost = "https://uk.api.csod.com/";
            log.info("CSOD [{}]: API host not found in page, using fallback: {}", tenantName, apiHost);
        }

        // Phase 2: paginate through the jobs API
        String jobsApiUrl = String.format(JOBS_API_TEMPLATE, apiHost);
        List<RawJobData> results = new ArrayList<>();
        int pageNumber = 1;
        int pageSize = 100;
        int totalCount = Integer.MAX_VALUE;
        int consecutiveBlankPages = 0;
        final int MAX_BLANK_PAGES = 3;

        while (results.size() < totalCount && pageNumber <= config.maxPages()) {
            log.info("CSOD [{}]: fetching jobs page {} ({} jobs so far / {} total)",
                    tenantName, pageNumber, results.size(), totalCount == Integer.MAX_VALUE ? "?" : totalCount);

            String requestBody = String.format(
                    "{\"careerSiteId\":%d,\"pageNumber\":%d,\"pageSize\":%d}",
                    siteId, pageNumber, pageSize);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwt);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(jobsApiUrl, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("CSOD [{}]: API returned {} on page {}", tenantName, response.getStatusCode(), pageNumber);
                break;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            totalCount = data.path("totalCount").asInt(0);
            JsonNode requisitions = data.path("requisitions");

            if (!requisitions.isArray() || requisitions.isEmpty()) {
                log.info("CSOD [{}]: no requisitions on page {}, stopping", tenantName, pageNumber);
                break;
            }

            int newOnPage = 0;
            for (JsonNode req : requisitions) {
                String reqId = req.path("req_id").asText(null);
                if (reqId == null) reqId = req.path("id").asText(null);
                if (reqId == null) continue;

                String guid = "csod-" + tenantName + "-" + reqId;
                if (config.isKnownGuid().test(guid)) {
                    // Seen alive — say so, or the expiry sweep will retire an open role.
                    config.onKnownJobSeen().accept(guid);
                    log.debug("CSOD [{}]: skipping known guid {}", tenantName, guid);
                    continue;
                }

                String title = req.path("title").asText("");
                String jobReqId = req.path("job_req_id").asText(reqId);
                String city = req.path("city").asText("");
                String country = req.path("country").asText("");
                String location = city.isBlank() ? country : (country.isBlank() ? city : city + ", " + country);
                String description = req.path("description").asText("");
                String datePosted = req.path("date_posted").asText(req.path("created_date").asText(""));

                String jobUrl = "https://" + tenantName + ".csod.com/ux/ats/careersite/" + siteId
                        + "/app?type=job&id=" + reqId + "&c=" + tenantName;

                String content = buildContent(title, tenantName, jobReqId, location, description, datePosted, jobUrl);

                results.add(new RawJobData(
                        JobSource.CORNERSTONE_ONDEMAND,
                        guid,
                        jobUrl,
                        content,
                        req.toString(),
                        Instant.now(),
                        List.of(),
                        null
                ));
                newOnPage++;
            }

            if (newOnPage == 0) {
                if (++consecutiveBlankPages >= MAX_BLANK_PAGES) {
                    log.info("CSOD [{}]: {} consecutive pages with no new jobs — stopping", tenantName, MAX_BLANK_PAGES);
                    break;
                }
            } else {
                consecutiveBlankPages = 0;
            }

            pageNumber++;
        }

        return results;
    }

    private String buildContent(String title, String tenant, String jobReqId,
                                 String location, String description,
                                 String datePosted, String url) {
        StringBuilder sb = new StringBuilder();
        if (!title.isBlank())      sb.append("Title: ").append(title).append('\n');
        sb.append("Company: ").append(tenant).append('\n');
        if (!jobReqId.isBlank())   sb.append("Req ID: ").append(jobReqId).append('\n');
        if (!location.isBlank())   sb.append("Location: ").append(location).append('\n');
        if (!datePosted.isBlank()) sb.append("Posted: ").append(datePosted).append('\n');
        sb.append("URL: ").append(url).append('\n');
        if (!description.isBlank()) {
            sb.append("Description: ").append(Jsoup.parse(description).text()).append('\n');
        }
        return sb.toString();
    }

    private String extractGroup(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : null;
    }
}
