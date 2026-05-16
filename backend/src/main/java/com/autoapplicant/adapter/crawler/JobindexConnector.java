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
import java.util.ArrayList;
import java.util.List;

@Component
public class JobindexConnector extends AbstractJobSourceConnector {

    private static final String RSS_URL = "https://www.jobindex.dk/jobsoegning.rss";
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";

    @Override
    public JobSource getSource() {
        return JobSource.JOBINDEX;
    }

    @Override
    public List<RawJobData> fetchJobs(CrawlConfig config) {
        List<RawJobData> results = new ArrayList<>();
        int maxJobs = config.maxPages() * 20;

        String rssContent;
        try {
            rssContent = Jsoup.connect(RSS_URL)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .execute()
                    .body();
        } catch (Exception e) {
            log.warn("Failed to fetch Jobindex RSS feed from {}: {}", RSS_URL, e.getMessage());
            return results;
        }

        Document rssDoc = Jsoup.parse(rssContent, "", Parser.xmlParser());
        Elements items = rssDoc.select("item");

        for (Element item : items) {
            if (results.size() >= maxJobs) {
                break;
            }

            String link = item.select("link").text();
            if (link == null || link.isBlank()) {
                link = item.select("link").first() != null
                        ? item.select("link").first().html()
                        : "";
            }
            String guid = item.select("guid").text();
            String description = item.select("description").text();

            if (guid == null || guid.isBlank()) {
                guid = link;
            }
            if (link.isBlank()) {
                log.warn("Jobindex RSS item has no link, skipping. guid={}", guid);
                continue;
            }

            String detailHtml = description;
            try {
                Thread.sleep(config.delayMs());
                Document detailDoc = Jsoup.connect(link)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .get();
                detailHtml = detailDoc.outerHtml();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while fetching Jobindex detail page, stopping crawl");
                break;
            } catch (Exception e) {
                log.warn("Failed to fetch Jobindex detail page {}: {} — using RSS description as fallback",
                        link, e.getMessage());
            }

            results.add(new RawJobData(
                    JobSource.JOBINDEX,
                    guid,
                    link,
                    detailHtml,
                    null,
                    Instant.now()
            ));
        }

        log.info("Jobindex crawl complete: {} jobs collected", results.size());
        return results;
    }
}
