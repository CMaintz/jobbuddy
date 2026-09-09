package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.domain.linkedin.LinkedInQueryPlan;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.linkedin.LinkedInQueryPlanRepositoryPort;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * LinkedIn job connector — reads the public {@code jobs-guest} endpoints (no auth,
 * no API key). Technique ported from ai-job-search's linkedin-search CLI, adapted to
 * this codebase's connector contract and using Jsoup selectors instead of regex.
 *
 * <p><b>Personal, low-volume use only.</b> Automated access is against LinkedIn's ToS;
 * this connector is deliberately kept off the shared crawl schedule
 * ({@link #includeInDefaultSchedule()} returns {@code false}), runs a rotated subset of
 * queries per run, pulls only recent postings, sleeps a randomised few seconds between
 * requests, and hard-caps detail fetches per run. It is driven by
 * {@code LinkedInCrawlScheduler} on its own jittered cadence, or by an explicit
 * per-source trigger.
 *
 * <p>Keywords come from each user's LLM-generated {@link LinkedInQueryPlan}; the connector
 * crosses them with the configured target locations to form the actual searches.
 */
@Component
public class LinkedInConnector extends AbstractJobSourceConnector {

    private static final String SEARCH_URL =
            "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search";
    private static final String DETAIL_URL =
            "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting";

    /** A real browser UA — the guest endpoints reject the default bot UA. */
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final LinkedInQueryPlanRepositoryPort planRepo;

    @Value("${app.linkedin.enabled:true}")
    private boolean enabled;

    /** Pipe-separated LinkedIn place strings (comma is part of a place name). */
    @Value("${app.linkedin.locations:Denmark}")
    private String locationsRaw;

    @Value("${app.linkedin.job-age-days:14}")
    private int jobAgeDays;

    @Value("${app.linkedin.max-pages-per-query:2}")
    private int maxPagesPerQuery;

    /** How many (keyword × location) searches to run per invocation — the rest wait for the next run. */
    @Value("${app.linkedin.queries-per-run:10}")
    private int queriesPerRun;

    /** Hard ceiling on new detail fetches per run — the primary volume guard. */
    @Value("${app.linkedin.max-jobs-per-run:40}")
    private int maxJobsPerRun;

    @Value("${app.linkedin.min-delay-ms:4000}")
    private long minDelayMs;

    @Value("${app.linkedin.max-delay-ms:9000}")
    private long maxDelayMs;

    public LinkedInConnector(LinkedInQueryPlanRepositoryPort planRepo) {
        this.planRepo = planRepo;
    }

    @Override
    public JobSource getSource() {
        return JobSource.LINKEDIN;
    }

    /** Never part of the bulk fan-out — runs only on its own controlled cadence. */
    @Override
    public boolean includeInDefaultSchedule() {
        return false;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        if (!enabled) {
            log.info("LinkedIn connector disabled (app.linkedin.enabled=false) — skipping");
            return;
        }

        List<String> keywords = collectKeywords();
        if (keywords.isEmpty()) {
            log.info("LinkedIn: no keywords in any query plan — nothing to crawl "
                    + "(generate plans first via LinkedInQueryPlanService)");
            return;
        }
        List<String> locations = parseLocations();

        // Build the full (keyword × location) search list, then rotate: a shuffled subset
        // runs this time so the wide net is covered across several runs, not all at once.
        List<String[]> searches = new ArrayList<>();
        for (String kw : keywords) {
            for (String loc : locations) {
                searches.add(new String[]{kw, loc});
            }
        }
        Collections.shuffle(searches);
        if (searches.size() > queriesPerRun) {
            searches = searches.subList(0, queriesPerRun);
        }

        log.info("LinkedIn: running {} of {}×{} possible searches this run (cap {} new jobs)",
                searches.size(), keywords.size(), locations.size(), maxJobsPerRun);

        Set<String> seenInRun = new LinkedHashSet<>();
        int fetched = 0;

        for (String[] search : searches) {
            if (fetched >= maxJobsPerRun) {
                log.info("LinkedIn: hit per-run cap of {} jobs — stopping", maxJobsPerRun);
                break;
            }
            fetched = runSearch(search[0], search[1], config, seenInRun, fetched);
        }

        log.info("LinkedIn crawl complete: {} new jobs ingested", fetched);
    }

    /** Paginates one keyword/location search and ingests new job details. Returns the updated fetch count. */
    private int runSearch(String keyword, String location, CrawlConfig config,
                          Set<String> seenInRun, int fetched) {
        for (int page = 1; page <= maxPagesPerQuery; page++) {
            if (fetched >= maxJobsPerRun) break;

            String url = buildSearchUrl(keyword, location, page);
            String html = fetchHtml(url);
            if (html == null || html.isBlank()) break;

            List<JobCard> cards = parseCards(html);
            if (cards.isEmpty()) break; // no more results for this query

            for (JobCard card : cards) {
                if (fetched >= maxJobsPerRun) break;
                if (!seenInRun.add(card.id)) continue;
                if (config.isKnownGuid().test(card.id)) {
                    config.onKnownJobSeen().accept(card.id);
                    continue;
                }

                sleepJitter();
                String detailHtml = fetchHtml(DETAIL_URL + "/" + card.id);
                if (detailHtml == null || detailHtml.isBlank()) continue;

                RawJobData raw = toRawJobData(card, detailHtml);
                if (raw == null) continue;
                config.onJobFound().accept(raw);
                fetched++;
            }
            sleepJitter();
        }
        return fetched;
    }

    // ── URL building ───────────────────────────────────────────────────────────

    private String buildSearchUrl(String keyword, String location, int page) {
        StringBuilder sb = new StringBuilder(SEARCH_URL).append('?');
        sb.append("keywords=").append(enc(keyword));
        sb.append("&location=").append(enc(location));
        String tpr = tprValue();
        if (tpr != null) sb.append("&f_TPR=").append(tpr);
        sb.append("&start=").append((page - 1) * 10);
        return sb.toString();
    }

    /** LinkedIn's recency filter: within N days → {@code r{seconds}}. */
    private String tprValue() {
        if (jobAgeDays <= 0 || jobAgeDays >= 9999) return null;
        return "r" + (jobAgeDays * 86400L);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Fetching ────────────────────────────────────────────────────────────────

    /** Jsoup GET with browser UA and 429/5xx backoff. Returns null on 404 / permanent failure. */
    private String fetchHtml(String url) {
        int maxRetries = 5;
        long delay = 800;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(delay + (long) (Math.random() * 700));
                    delay = Math.min(delay * 2, 8_000);
                }
                Connection.Response res = Jsoup.connect(url)
                        .userAgent(BROWSER_UA)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .timeout(15_000)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .followRedirects(true)
                        .execute();
                int status = res.statusCode();
                if (status == 404) return null;
                if (status == 429 || status >= 500) {
                    log.warn("LinkedIn fetch attempt {} got HTTP {} for {}", attempt + 1, status, url);
                    continue;
                }
                if (status >= 400) {
                    log.warn("LinkedIn non-retryable HTTP {} for {}", status, url);
                    return null;
                }
                return res.body();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("LinkedIn fetch interrupted for {}", url);
                return null;
            } catch (Exception e) {
                log.warn("LinkedIn fetch attempt {} failed for {}: {}", attempt + 1, url, e.getMessage());
            }
        }
        log.warn("LinkedIn: retries exhausted for {}", url);
        return null;
    }

    private void sleepJitter() {
        long lo = Math.max(0, minDelayMs);
        long hi = Math.max(lo, maxDelayMs);
        long ms = lo + (long) (Math.random() * (hi - lo));
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Parsing ──────────────────────────────────────────────────────────────────

    private record JobCard(String id, String title, String company, String location,
                           String url, Instant postedAt) {}

    private List<JobCard> parseCards(String html) {
        List<JobCard> cards = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        for (Element card : doc.select("[data-entity-urn*=jobPosting]")) {
            String urn = card.attr("data-entity-urn");
            String id = urn.substring(urn.lastIndexOf(':') + 1).trim();
            if (id.isEmpty() || !id.chars().allMatch(Character::isDigit)) continue;

            String title = text(card.selectFirst(".base-search-card__title"));
            if (title == null) title = text(card.selectFirst(".sr-only"));
            if (title == null) continue;

            String company = text(card.selectFirst(".base-search-card__subtitle"));
            String location = text(card.selectFirst(".job-search-card__location"));

            Element link = card.selectFirst("a.base-card__full-link");
            String url = link != null ? link.attr("href").split("\\?")[0]
                    : "https://www.linkedin.com/jobs/view/" + id;

            Element time = card.selectFirst("time");
            Instant postedAt = parseDate(time != null ? time.attr("datetime") : null);

            cards.add(new JobCard(id, title, company, location, url, postedAt));
        }
        return cards;
    }

    /**
     * Builds a {@link RawJobData} from the card plus the detail page. Wraps the parsed
     * fields in a normalised HTML doc (title in {@code <title>}/{@code <h1>}) so the shared
     * {@code IngestionPipeline}/{@code TextCleaningService} extract the title and body cleanly.
     */
    private RawJobData toRawJobData(JobCard card, String detailHtml) {
        Document doc = Jsoup.parse(detailHtml);

        String title = firstNonBlank(
                text(doc.selectFirst(".top-card-layout__title")),
                text(doc.selectFirst(".topcard__title")),
                card.title);

        Element orgLink = doc.selectFirst(".topcard__org-name-link");
        String company = firstNonBlank(
                orgLink != null ? orgLink.text().trim() : null,
                text(doc.selectFirst(".topcard__flavor")),
                card.company);

        String location = firstNonBlank(
                text(doc.selectFirst(".topcard__flavor--bullet")),
                card.location);

        Element descEl = doc.selectFirst(".show-more-less-html__markup");
        if (descEl == null) descEl = doc.selectFirst(".description__text");
        String description = descEl != null ? descEl.text().trim() : "";

        // Fold LinkedIn's job-criteria (seniority, employment type, function, industries)
        // into the body so downstream AI enrichment can use them.
        StringBuilder criteria = new StringBuilder();
        for (Element item : doc.select(".description__job-criteria-item")) {
            String label = text(item.selectFirst(".description__job-criteria-subheader"));
            String value = text(item.selectFirst(".description__job-criteria-text"));
            if (label != null && value != null) {
                criteria.append(label).append(": ").append(value).append(". ");
            }
        }

        if (description.isBlank() && criteria.length() == 0) {
            log.debug("LinkedIn: empty detail for job {} — skipping", card.id);
            return null;
        }

        String rawHtml = "<html><head><title>" + esc(title) + "</title></head><body>"
                + "<h1>" + esc(title) + "</h1>"
                + "<div class=\"company\">" + esc(company) + "</div>"
                + "<div class=\"location\">" + esc(location) + "</div>"
                + "<div class=\"criteria\">" + esc(criteria.toString()) + "</div>"
                + "<div class=\"description\">" + esc(description) + "</div>"
                + "</body></html>";

        String shortDescription = description.isBlank() ? null
                : (description.length() > 400 ? description.substring(0, 400) + "…" : description);

        return new RawJobData(JobSource.LINKEDIN, card.id, card.url, rawHtml, null,
                Instant.now(), List.of(), shortDescription,
                null,                                   // deadline — guest endpoints don't expose it
                blankToNull(company),
                null,                                   // company website — not available here
                blankToNull(location),
                card.postedAt);
    }

    // ── Small helpers ─────────────────────────────────────────────────────────────

    private List<String> collectKeywords() {
        Set<String> all = new LinkedHashSet<>();
        for (LinkedInQueryPlan plan : planRepo.findAll()) {
            if (plan.keywords() != null) {
                for (String k : plan.keywords()) {
                    if (k != null && !k.isBlank()) all.add(k.trim());
                }
            }
        }
        return new ArrayList<>(all);
    }

    private List<String> parseLocations() {
        List<String> out = new ArrayList<>();
        for (String part : locationsRaw.split("\\|")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        if (out.isEmpty()) out.add("Denmark");
        return out;
    }

    private static Instant parseDate(String raw) {
        if (raw == null || raw.length() < 10) return null;
        try {
            return LocalDate.parse(raw.substring(0, 10)).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(Element el) {
        if (el == null) return null;
        String t = el.text().trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
