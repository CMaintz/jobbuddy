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

@Component
public class ItJobbankConnector extends AbstractJobSourceConnector {

    private static final String RSS_BASE = "https://www.it-jobbank.dk/jobsoegning.rss";
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";

    @Override
    public JobSource getSource() {
        return JobSource.IT_JOBBANK;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        int maxJobs = config.maxPages() * 20;
        int count = 0;
        int consecutiveBlankPages = 0;
        final int MAX_BLANK_PAGES = 3;

        outer:
        for (int page = 0; page < config.maxPages() && count < maxJobs; page++) {
            log.info("IT-Jobbank: fetching RSS page {} [{}/{}] ({} jobs so far)",
                    page, page + 1, config.maxPages(), count);

            String rssContent;
            try {
                rssContent = Jsoup.connect(RSS_BASE + "?p=" + page)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .execute()
                        .body();
            } catch (Exception e) {
                log.warn("Failed to fetch IT-Jobbank RSS page {}: {}", page, e.getMessage());
                break;
            }

            Document rssDoc = Jsoup.parse(rssContent, "", Parser.xmlParser());
            Elements items = rssDoc.select("item");
            if (items.isEmpty()) {
                log.info("IT-Jobbank: no items on RSS page {}, stopping", page);
                break;
            }
            log.info("IT-Jobbank: {} items on page {}, fetching detail pages...", items.size(), page);

            int newOnPage = 0;
            for (Element item : items) {
                if (count >= maxJobs) break outer;

                String link = item.select("link").first() != null
                        ? item.select("link").first().html().trim()
                        : item.select("link").text().trim();
                String guid = item.select("guid").text();
                if (guid == null || guid.isBlank()) guid = link;
                if (link.isBlank()) {
                    log.warn("IT-Jobbank RSS item has no link, skipping. guid={}", guid);
                    continue;
                }

                if (config.isKnownGuid().test(guid)) {
                    log.debug("IT-Jobbank: skipping known guid on page {}", page);
                    continue;
                }

                String detailHtml = item.select("description").text();
                try {
                    Thread.sleep(config.delayMs());
                    detailHtml = Jsoup.connect(link)
                            .userAgent(USER_AGENT)
                            .timeout(15000)
                            .get()
                            .outerHtml();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted fetching IT-Jobbank detail page, stopping crawl");
                    break outer;
                } catch (Exception e) {
                    log.warn("Failed to fetch IT-Jobbank detail page {}: {} — using RSS description", link, e.getMessage());
                }

                config.onJobFound().accept(new RawJobData(JobSource.IT_JOBBANK, guid, link, detailHtml, null, Instant.now()));
                count++;
                newOnPage++;
            }

            if (newOnPage == 0) {
                if (++consecutiveBlankPages >= MAX_BLANK_PAGES) {
                    log.info("IT-Jobbank: {} consecutive pages with no new jobs — stopping", MAX_BLANK_PAGES);
                    break;
                }
            } else {
                consecutiveBlankPages = 0;
            }
        }

        log.info("IT-Jobbank crawl complete: {} jobs collected", count);
    }
}
