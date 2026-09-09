package com.autoapplicant.adapter.crawler;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fetches job listings from Teamtailor career pages via RSS.
 *
 * <p>Each configured career page URL exposes a per-company RSS feed at
 * {@code {baseUrl}/jobs.rss} (no auth required, updated ~3×/day). The feed
 * is paginated via {@code ?offset=N&per_page=100}.
 *
 * <p>RSS {@code <description>} contains the full job description as HTML.
 * Teamtailor-specific fields are exposed under the {@code tt:} namespace:
 * {@code tt:locations}, {@code tt:department}, {@code tt:role}. Remote status
 * is available as {@code <remoteStatus>}. No detail page fetch needed.
 *
 * <p>Career page URLs are configured via {@code app.teamtailor.career-page-urls}.
 */
@Component
public class TeamtailorConnector extends AbstractJobSourceConnector {

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int RSS_PAGE_SIZE      = 100;

    private final AppProperties appProperties;

    public TeamtailorConnector(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public JobSource getSource() {
        return JobSource.TEAMTAILOR;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        List<String> careerPageUrls = appProperties.getTeamtailor().getCareerPageUrls();
        if (careerPageUrls.isEmpty()) {
            log.info("Teamtailor: no career page URLs configured (app.teamtailor.career-page-urls) — skipping");
            return;
        }

        Set<String> seenInRun = new HashSet<>();
        int totalCount = 0;

        for (String baseUrl : careerPageUrls) {
            totalCount += crawlCompanyRss(baseUrl.replaceAll("/$", ""), config, seenInRun);
        }

        log.info("Teamtailor crawl complete: {} jobs collected", totalCount);
    }

    private int crawlCompanyRss(String baseUrl, CrawlConfig config, Set<String> seenInRun) {
        int count = 0;

        for (int offset = 0; ; offset += RSS_PAGE_SIZE) {
            String rssUrl = baseUrl + "/jobs.rss?offset=" + offset + "&per_page=" + RSS_PAGE_SIZE;

            String rssBody;
            try {
                rssBody = Jsoup.connect(rssUrl)
                        .userAgent(USER_AGENT)
                        .timeout(CONNECT_TIMEOUT_MS)
                        .execute()
                        .body();
            } catch (Exception e) {
                log.warn("Teamtailor: failed to fetch RSS {}: {}", rssUrl, e.getMessage());
                break;
            }

            Document rssDoc = Jsoup.parse(rssBody, "", Parser.xmlParser());
            Elements items  = rssDoc.select("item");

            log.info("Teamtailor: RSS page offset={} returned {} items for {}", offset, items.size(), baseUrl);

            if (items.isEmpty()) {
                break;
            }

            int newOnPage = 0;

            for (Element item : items) {
                String link = extractRssLink(item);
                String guid = item.select("guid").text().trim();
                if (guid.isBlank()) guid = link;
                if (link.isBlank()) link = guid;

                if (link.isBlank()) {
                    log.warn("Teamtailor: RSS item has no link or guid — title={}", item.select("title").text());
                    continue;
                }

                if (seenInRun.contains(guid)) continue;

                if (config.isKnownGuid().test(guid)) {
                    config.onKnownJobSeen().accept(guid);
                    log.debug("Teamtailor: skipping known guid {}", guid);
                    continue;
                }

                seenInRun.add(guid);

                // Teamtailor uses tt:department rather than the standard <category> element
                List<String> categories = item.getElementsByTag("tt:department").eachText();

                config.onJobFound().accept(new RawJobData(
                        JobSource.TEAMTAILOR,
                        guid,
                        link,
                        buildContent(item, link),
                        null,
                        Instant.now(),
                        categories,
                        null
                ));
                count++;
                newOnPage++;

                try {
                    Thread.sleep(config.delayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return count;
                }
            }

            if (!config.force() && newOnPage == 0) {
                log.info("Teamtailor: all items on page (offset={}) were known — stopping for {}", offset, baseUrl);
                break;
            }
        }

        return count;
    }

    private String buildContent(Element item, String link) {
        String title      = item.select("title").text().trim();
        String rawDesc    = item.select("description").text().trim();
        String desc       = rawDesc.isBlank() ? "" : Jsoup.parse(rawDesc).text();
        String locations  = item.getElementsByTag("tt:locations").text().trim();
        String department = item.getElementsByTag("tt:department").text().trim();
        String role       = item.getElementsByTag("tt:role").text().trim();
        String remote     = item.select("remoteStatus").text().trim();

        StringBuilder sb = new StringBuilder();
        if (!title.isBlank())      sb.append("Title: ").append(title).append('\n');
        if (!link.isBlank())       sb.append("URL: ").append(link).append('\n');
        if (!department.isBlank()) sb.append("Department: ").append(department).append('\n');
        if (!role.isBlank())       sb.append("Role: ").append(role).append('\n');
        if (!locations.isBlank())  sb.append("Location: ").append(locations).append('\n');
        if (!remote.isBlank())     sb.append("Remote status: ").append(remote).append('\n');
        if (!desc.isBlank())       sb.append("Description: ").append(desc).append('\n');
        return sb.length() > 0 ? sb.toString() : desc;
    }
}
