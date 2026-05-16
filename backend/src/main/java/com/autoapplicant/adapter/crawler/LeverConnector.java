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
 * Fetches job listings from Lever ATS job boards.
 *
 * Endpoint: https://api.lever.co/v0/postings/{company}?mode=json
 * No authentication required — this is a public-facing postings API.
 *
 * Companies are configured via app.lever.companies.
 * The company slug is the one in Lever job URLs:
 *   https://jobs.lever.co/dixa  →  slug = "dixa"
 */
@Component
public class LeverConnector extends AbstractJobSourceConnector {

    private static final String API_BASE = "https://api.lever.co/v0/postings/%s?mode=json";

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    public LeverConnector(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public JobSource getSource() {
        return JobSource.LEVER;
    }

    @Override
    public List<RawJobData> fetchJobs(CrawlConfig config) {
        List<String> companies = appProperties.getLever().getCompanies();
        if (companies.isEmpty()) {
            log.info("Lever: no companies configured (app.lever.companies) — skipping");
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

                if (!root.isArray()) {
                    log.warn("Lever: unexpected response for company '{}' — not an array", company);
                    continue;
                }

                int count = 0;
                for (JsonNode posting : root) {
                    if (results.size() >= targetJobs) break;

                    String id         = posting.path("id").asText(null);
                    String title      = posting.path("text").asText("");
                    String hostedUrl  = posting.path("hostedUrl").asText(null);
                    String team       = posting.path("categories").path("team").asText("");
                    String location   = posting.path("categories").path("location").asText("");
                    String commitment = posting.path("categories").path("commitment").asText(""); // full-time, part-time etc.
                    String descPlain  = posting.path("descriptionPlain").asText("");
                    String descHtml   = posting.path("description").asText("");

                    if (id == null || hostedUrl == null) continue;

                    // Additional lists (requirements, responsibilities, etc.)
                    StringBuilder listsText = new StringBuilder();
                    JsonNode lists = posting.path("lists");
                    if (lists.isArray()) {
                        for (JsonNode list : lists) {
                            String listTitle = list.path("text").asText("");
                            String listHtml  = list.path("content").asText("");
                            if (!listTitle.isBlank()) {
                                listsText.append(listTitle).append(":\n");
                                listsText.append(Jsoup.parse(listHtml).text()).append("\n\n");
                            }
                        }
                    }

                    String content = buildContent(title, company, location, team, commitment,
                            descPlain.isBlank() ? Jsoup.parse(descHtml).text() : descPlain,
                            listsText.toString(), hostedUrl);

                    results.add(new RawJobData(
                            JobSource.LEVER,
                            "lever-" + company + "-" + id,
                            hostedUrl,
                            content,
                            posting.toString(),
                            Instant.now()
                    ));
                    count++;
                }
                log.info("Lever: {} — {} jobs collected", company, count);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Lever: interrupted, returning {} results so far", results.size());
                break;
            } catch (Exception e) {
                log.warn("Lever: failed to fetch company '{}': {}", company, e.getMessage());
            }
        }

        log.info("Lever crawl complete: {} jobs from {} companies", results.size(), companies.size());
        return results;
    }

    private String buildContent(String title, String company, String location, String team,
                                String commitment, String description, String lists, String url) {
        StringBuilder sb = new StringBuilder();
        if (!title.isBlank())       sb.append("Title: ").append(title).append('\n');
        sb.append("Company: ").append(company).append('\n');
        if (!location.isBlank())    sb.append("Location: ").append(location).append('\n');
        if (!team.isBlank())        sb.append("Team: ").append(team).append('\n');
        if (!commitment.isBlank())  sb.append("Employment Type: ").append(commitment).append('\n');
        sb.append("URL: ").append(url).append('\n');
        if (!description.isBlank()) sb.append("Description: ").append(description).append('\n');
        if (!lists.isBlank())       sb.append(lists);
        return sb.toString();
    }
}
