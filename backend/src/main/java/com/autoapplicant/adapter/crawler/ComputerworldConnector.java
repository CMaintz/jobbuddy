package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ComputerworldConnector extends AbstractJobSourceConnector {

    private static final String PORTAL_URL = "https://www.computerworld.dk/jobportal";
    private static final String BASE_URL = "https://www.computerworld.dk";
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";

    @Override
    public JobSource getSource() {
        return JobSource.COMPUTERWORLD;
    }

    @Override
    public List<RawJobData> fetchJobs(CrawlConfig config) {
        List<RawJobData> results = new ArrayList<>();
        int maxJobs = config.maxPages() * 10;

        Document listPage;
        try {
            listPage = Jsoup.connect(PORTAL_URL)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .get();
        } catch (Exception e) {
            log.warn("Computerworld: failed to fetch job portal {}: {}", PORTAL_URL, e.getMessage());
            return results;
        }

        // Try multiple selectors, use first that yields results
        Elements jobElements = new Elements();
        String[] selectors = {".job-listing", ".jobcard", "article.job", ".job-item"};
        for (String selector : selectors) {
            jobElements = listPage.select(selector);
            if (!jobElements.isEmpty()) {
                log.info("Computerworld: using selector '{}', found {} elements", selector, jobElements.size());
                break;
            }
        }

        if (jobElements.isEmpty()) {
            log.warn("Computerworld: no job elements found on {} with any known selector", PORTAL_URL);
            return results;
        }

        for (Element jobEl : jobElements) {
            if (results.size() >= maxJobs) {
                break;
            }

            Element linkEl = jobEl.selectFirst("a[href]");
            if (linkEl == null) {
                continue;
            }

            String href = linkEl.attr("href");
            if (href.isBlank()) {
                continue;
            }

            // Ensure absolute URL
            String absoluteUrl = href.startsWith("http") ? href : BASE_URL + href;

            // sourceJobId: path segment after last '/'
            String sourceJobId = absoluteUrl.replaceAll("[?#].*", "").replaceAll(".*/", "");
            if (sourceJobId.isBlank()) {
                sourceJobId = absoluteUrl;
            }

            String detailHtml = jobEl.outerHtml();
            try {
                Thread.sleep(config.delayMs());
                Document detailDoc = Jsoup.connect(absoluteUrl)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .get();
                detailHtml = detailDoc.outerHtml();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while fetching Computerworld detail pages, returning partial results");
                break;
            } catch (Exception e) {
                log.warn("Computerworld: failed to fetch detail page {}: {} — using listing snippet as fallback",
                        absoluteUrl, e.getMessage());
            }

            results.add(new RawJobData(
                    JobSource.COMPUTERWORLD,
                    sourceJobId,
                    absoluteUrl,
                    detailHtml,
                    null,
                    Instant.now()
            ));
        }

        log.info("Computerworld crawl complete: {} jobs collected", results.size());
        return results;
    }
}
