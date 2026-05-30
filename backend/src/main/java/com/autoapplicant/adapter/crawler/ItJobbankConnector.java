package com.autoapplicant.adapter.crawler;

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

@Component
public class ItJobbankConnector extends AbstractJobSourceConnector {

    private static final String RSS_BASE = "https://www.it-jobbank.dk/jobsoegning.rss";

    @Override
    public JobSource getSource() {
        return JobSource.IT_JOBBANK;
    }

    /** Incremental mode stops after this many consecutive all-known pages. */
    private static final int CONSECUTIVE_KNOWN_THRESHOLD = 2;

    @Override
    public void fetchJobs(CrawlConfig config) {
        int totalCount = 0;
        Set<String> seenInRun = new HashSet<>();
        int consecutiveKnownPages = 0;

        for (int page = 1; ; page++) {
            String url = RSS_BASE + "?page=" + page;
            log.info("IT-Jobbank: fetching page {} ({} jobs so far)", page, totalCount);

            String rssContent;
            try {
                rssContent = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .execute()
                        .body();
            } catch (Exception e) {
                log.warn("Failed to fetch IT-Jobbank RSS page {}: {}", url, e.getMessage());
                break;
            }

            Document rssDoc = Jsoup.parse(rssContent, "", Parser.xmlParser());
            Elements items = rssDoc.select("item");
            if (items.isEmpty()) {
                log.info("IT-Jobbank: empty page {} — feed exhausted", page);
                break;
            }

            int newOnPage = 0;

            for (Element item : items) {
                String link = extractRssLink(item);
                String guid = item.select("guid").text().trim();
                if (guid.isBlank()) guid = link;

                if (link.isBlank()) {
                    log.warn("IT-Jobbank RSS item has no link, skipping. guid={}", guid);
                    continue;
                }

                if (seenInRun.contains(guid)) continue;

                if (config.isKnownGuid().test(guid)) {
                    log.debug("IT-Jobbank: skipping known guid {}", guid);
                    continue;
                }

                seenInRun.add(guid);

                // Extract short description from RSS <description> <p> tags
                String shortDescription = extractShortDescription(item);

                String detailHtml = item.select("description").text();
                try {
                    Thread.sleep(config.delayMs());
                    detailHtml = Jsoup.connect(link)
                            .userAgent(USER_AGENT)
                            .timeout(15000)
                            .followRedirects(true)
                            .get()
                            .outerHtml();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted fetching IT-Jobbank detail page, stopping crawl");
                    return;
                } catch (Exception e) {
                    log.warn("Failed to fetch IT-Jobbank detail page {}: {} — using RSS description",
                            link, e.getMessage());
                }

                List<String> rssCategories = item.select("category").eachText();
                config.onJobFound().accept(new RawJobData(JobSource.IT_JOBBANK, guid, link, detailHtml, null, Instant.now(), rssCategories, shortDescription));
                totalCount++;
                newOnPage++;
            }

            if (!config.force()) {
                if (newOnPage == 0) {
                    consecutiveKnownPages++;
                    log.info("IT-Jobbank: page {} all-known ({}/{} consecutive)",
                            page, consecutiveKnownPages, CONSECUTIVE_KNOWN_THRESHOLD);
                    if (consecutiveKnownPages >= CONSECUTIVE_KNOWN_THRESHOLD) {
                        log.info("IT-Jobbank: caught up — stopping");
                        break;
                    }
                } else {
                    consecutiveKnownPages = 0;
                }
            }
        }

        log.info("IT-Jobbank crawl complete: {} jobs collected", totalCount);
    }

    /**
     * Extracts a short teaser from the RSS {@code <description>} element.
     * IT-Jobbank wraps the description in HTML with {@code <p>} tags.
     */
    private String extractShortDescription(Element item) {
        String rawDesc = item.select("description").text();
        if (rawDesc.isBlank()) return null;
        org.jsoup.nodes.Document descDoc = Jsoup.parse(rawDesc);
        org.jsoup.select.Elements paragraphs = descDoc.select("p");
        if (paragraphs.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (org.jsoup.nodes.Element p : paragraphs) {
            String text = p.text().trim();
            if (!text.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(text);
                if (sb.length() >= 300) break;
            }
        }
        String result = sb.toString().trim();
        return result.isBlank() ? null : (result.length() > 400 ? result.substring(0, 400) + "…" : result);
    }

}
