package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JobindexConnector extends AbstractJobSourceConnector {

    private static final String RSS_BASE = "https://www.jobindex.dk/jobsoegning.rss";

    /**
     * Jobindex category RSS feeds, paginated per subid.
     *
     * Pagination: use {@code ?subid=N&page=M} (1-indexed). The {@code ?p=N}
     * parameter is silently ignored — {@code page=} is the correct parameter.
     * Each page returns up to 20 items; an empty page signals the feed is exhausted.
     *
     * Subids discovered from Jobindex page source JS:
     *   "link_rss":"https://www.jobindex.dk/jobsoegning.rss?subid=N"
     * The full list comes from the page's subjobcategory_list JS object.
     */
    private static final List<Integer> SUBIDS = List.of(
            // IT / Software
            1,   // Systemudvikling og programmering
            2,   // Økonomi- og virksomhedssystemer (IT ERP/business systems)
            3,   // IT-ledelse
            4,   // IT-drift og support
            6,   // Internet og WWW
            7,   // Tele- og datakommunikation
            93,  // Database
            // Engineering
            8,   // Bygge- og anlægsteknik
            11,  // Elektroteknik
            24,  // Bygge og anlæg
            40,  // Tømrer og snedker
            80,  // Elektriker
            85,  // Maskinteknik
            90,  // Jern og metal
            94,  // Kemi og bioteknik
            95,  // Mekanik og auto
            96,  // Blik og rør
            97,  // Maling og overfladebehandling
            104, // Træ- og møbelindustri
            121, // Ledelse inden for ingeniør og teknik
            122, // Produktions- og procesteknik
            // Finance
            35,  // Finans og forsikring
            33,  // Økonomi og regnskab
            81,  // Økonomiledelse
            // HR
            12,  // Personale og HR
            // Management
            13,  // Topledelse og bestyrelse
            14,  // Ledelse
            61,  // Projektledelse
            38,  // Offentlig administration
            79,  // Institutions- og skoleledelse
            124, // Detailledelse
            125, // Virksomhedsudvikling
            // Healthcare
            16,  // Læge
            17,  // Sygeplejerske og jordemoder
            41,  // Terapi og genoptræning
            47,  // Pleje og omsorg
            51,  // Tandlæge og klinikpersonale
            63,  // Lægesekretær
            77,  // Socialrådgivning
            91,  // Psykologi og psykiatri
            100, // Teknisk sundhedsarbejde
            10,  // Medicinal og levnedsmiddel
            // Education
            27,  // Pædagog
            28,  // Lærer
            45,  // Forskning
            37,  // Bibliotek
            23,  // Børnepasning
            103, // Voksenuddannelse
            126, // Akademisk og politisk arbejde
            // Marketing / Communications
            55,  // Marketing
            49,  // Kommunikation og journalistik
            // Sales
            58,  // Salg
            75,  // Salgsledelse
            57,  // Telemarketing
            60,  // Ejendomsmægler
            70,  // Detailhandel
            // Logistics / Operations
            53,  // Indkøb
            54,  // Logistik og spedition
            74,  // Lager
            83,  // Transport
            44,  // Industriel produktion
            98,  // Ejendomsservice
            112, // Sikkerhed
            // Customer Service
            71,  // Service
            // Legal
            52,  // Jura
            // Design / Creative
            89,  // Grafisk
            110, // Design og formgivning
            // Admin / Office
            18,  // Kontor
            21,  // Sekretær og reception
            106, // Oversættelse og sprog
            // Other / Miscellaneous
            25,  // Landbrug, skov og fiskeri
            36,  // Nærings- og nydelsesmiddel
            65,  // Kultur og kirke
            67,  // Hotel, restaurant og køkken
            73,  // Rengøring
            56,  // Frisør og personlig pleje
            92,  // Tekstil og kunsthåndværk
            99,  // Bud og udbringning
            120, // Selvstændig virksomhedsdrift
            127, // Forsvar og efterretning
            15   // Øvrige (catch-all)
    );

    @Override
    public JobSource getSource() {
        return JobSource.JOBINDEX;
    }

    @Override
    public void fetchJobs(CrawlConfig config) {
        int totalCount = 0;

        // Dedup within a single run — same job can appear in the base feed
        // and in one or more subid feeds.
        Set<String> seenInRun = new HashSet<>();

        // ── Phase 0: structured metadata from the search page Stash blob ──────
        // The RSS feeds carry no deadline or company homepage; the search page
        // embeds both per result. Best-effort: a miss just means null metadata.
        Map<String, StashMeta> stashMeta = collectStashMetadata(config.force() ? 150 : 25);

        // ── Phase 1: base feed (all categories, most-recent first) ────────────
        // Capped at 50 pages = 1,000 jobs by the Jobindex RSS endpoint.
        log.info("Jobindex: phase 1 — base feed");
        totalCount = crawlFeed(null, config, totalCount, seenInRun, stashMeta);

        // ── Phase 2: per-subid feeds (deeper history per category) ────────────
        // Each subid is also capped at 50 pages = 1,000 jobs. Jobs already
        // collected in phase 1 are skipped via seenInRun / isKnownGuid.
        log.info("Jobindex: phase 2 — subid feeds ({} jobs so far)", totalCount);
        for (int subid : SUBIDS) {
            totalCount = crawlFeed(subid, config, totalCount, seenInRun, stashMeta);
        }

        log.info("Jobindex crawl complete: {} jobs collected", totalCount);
    }

    /** Incremental mode stops after this many consecutive all-known pages. */
    private static final int CONSECUTIVE_KNOWN_THRESHOLD = 2;

    /**
     * Paginates one RSS feed (base feed when {@code subid} is null, or a category
     * feed when it is set) until the feed returns an empty page (force mode) or
     * {@link #CONSECUTIVE_KNOWN_THRESHOLD} consecutive all-known pages are seen
     * (incremental mode).
     *
     * @return updated total count
     */
    private int crawlFeed(Integer subid, CrawlConfig config,
                          int totalCount, Set<String> seenInRun, Map<String, StashMeta> stashMeta) {
        int consecutiveKnownPages = 0;

        for (int page = 1; ; page++) {
            String url = subid == null
                    ? RSS_BASE + "?page=" + page
                    : RSS_BASE + "?subid=" + subid + "&page=" + page;

            if (subid == null) {
                log.info("Jobindex: base feed page={} ({} jobs so far)", page, totalCount);
            } else {
                log.info("Jobindex: subid={} page={} ({} jobs so far)", subid, page, totalCount);
            }

            String rssContent;
            try {
                rssContent = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .execute()
                        .body();
            } catch (Exception e) {
                log.warn("Failed to fetch Jobindex RSS {}: {}", url, e.getMessage());
                break;
            }

            Document rssDoc = Jsoup.parse(rssContent, "", Parser.xmlParser());
            Elements items = rssDoc.select("item");
            if (items.isEmpty()) {
                log.info("Jobindex: empty page {} — feed exhausted (subid={})", page, subid);
                break;
            }

            int newOnPage = 0;

            for (Element item : items) {
                String link = extractRssLink(item);
                String guid = item.select("guid").text().trim();
                if (guid.isBlank()) guid = link;

                if (link.isBlank()) {
                    log.warn("Jobindex RSS item has no link, skipping. guid={}", guid);
                    continue;
                }

                if (seenInRun.contains(guid)) continue;

                if (config.isKnownGuid().test(guid)) {
                    log.debug("Jobindex: skipping known guid {}", guid);
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
                    log.warn("Interrupted fetching Jobindex detail page, stopping crawl");
                    return totalCount;
                } catch (Exception e) {
                    log.warn("Failed to fetch Jobindex detail page {}: {} — using RSS description",
                            link, e.getMessage());
                }

                List<String> rssCategories = item.select("category").eachText();
                StashMeta meta = stashMeta.get(extractTid(link));
                config.onJobFound().accept(new RawJobData(JobSource.JOBINDEX, guid, link, detailHtml,
                        null, Instant.now(), rssCategories, shortDescription,
                        meta != null ? meta.deadline() : null,
                        meta != null ? meta.companyName() : null,
                        meta != null ? meta.companyHomeUrl() : null));
                totalCount++;
                newOnPage++;
            }

            // Incremental mode: stop after CONSECUTIVE_KNOWN_THRESHOLD all-known
            // pages in a row. A single all-known page can just be overlap with the
            // base feed; requiring two consecutive ones avoids stopping prematurely
            // before reaching older subid-specific jobs.
            // Force mode: run until the feed returns an empty page.
            if (!config.force()) {
                if (newOnPage == 0) {
                    consecutiveKnownPages++;
                    log.info("Jobindex: subid={} page={} all-known ({}/{} consecutive)",
                            subid, page, consecutiveKnownPages, CONSECUTIVE_KNOWN_THRESHOLD);
                    if (consecutiveKnownPages >= CONSECUTIVE_KNOWN_THRESHOLD) {
                        log.info("Jobindex: subid={} caught up — stopping", subid);
                        break;
                    }
                } else {
                    consecutiveKnownPages = 0;
                }
            }
        }

        return totalCount;
    }

    /**
     * Extracts a short teaser from the RSS {@code <description>} element.
     * Jobindex wraps the description in HTML with {@code <p>} tags; we join
     * the paragraph texts to produce a 1-2 sentence card preview.
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

    // ── Stash metadata (deadline + company homepage) ──────────────────────────
    //
    // Jobindex moved search results client-side: the /jobsoegning HTML embeds
    // the result payload in a `var Stash = {...}` script blob, under
    // …→ storeData → searchResponse → { results[] }. Each result carries
    // structured fields the RSS feeds lack: apply_deadline, company.homeurl.
    // (Technique ported from ai-job-search-master's jobindex CLI.)

    record StashMeta(LocalDate deadline, String companyName, String companyHomeUrl) {}

    private static final String SEARCH_PAGE = "https://www.jobindex.dk/jobsoegning?page=";
    private static final Pattern TID_PATTERN = Pattern.compile("/jobannonce/([^/?#]+)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** tid from a /jobannonce/{tid}… URL, or null. */
    static String extractTid(String url) {
        if (url == null) return null;
        Matcher m = TID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** Paginates the search page and maps tid → structured metadata. Best-effort. */
    private Map<String, StashMeta> collectStashMetadata(int maxPages) {
        Map<String, StashMeta> meta = new HashMap<>();
        for (int page = 1; page <= maxPages; page++) {
            try {
                String html = Jsoup.connect(SEARCH_PAGE + page)
                        .userAgent(USER_AGENT).timeout(15000).execute().body();
                JsonNode results = findSearchResults(extractStash(html));
                if (results == null || !results.isArray() || results.isEmpty()) break;
                for (JsonNode r : results) {
                    String tid = r.path("tid").asText(null);
                    if (tid == null || tid.isBlank()) continue;
                    meta.put(tid, new StashMeta(
                            parseStashDeadline(r),
                            firstNonBlank(r.path("company").path("name").asText(null),
                                    r.path("companytext").asText(null)),
                            r.path("company").path("homeurl").asText(null)));
                }
            } catch (Exception e) {
                log.warn("Jobindex: stash metadata page {} failed: {} — continuing without", page, e.getMessage());
                break;
            }
        }
        log.info("Jobindex: stash metadata collected for {} jobs", meta.size());
        return meta;
    }

    /** ASAP deadlines stay null — only a concrete date is stored. */
    private static LocalDate parseStashDeadline(JsonNode r) {
        if (r.path("apply_deadline_asap").asBoolean(false)) return null;
        for (String field : new String[]{"apply_deadline", "lastdate"}) {
            String raw = r.path(field).asText(null);
            if (raw != null && raw.length() >= 10) {
                try { return LocalDate.parse(raw.substring(0, 10)); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }

    /**
     * Extracts the JSON object following {@code var Stash = } with a brace-counting
     * scan (string- and escape-aware — the blob contains braces inside strings).
     */
    JsonNode extractStash(String html) throws Exception {
        String marker = "var Stash = ";
        int start = html.indexOf(marker);
        if (start == -1) throw new IllegalStateException("No Stash blob in Jobindex HTML");
        int open = start + marker.length();
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = open; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
            } else {
                if (c == '"') inStr = true;
                else if (c == '{') depth++;
                else if (c == '}' && --depth == 0) {
                    return objectMapper.readTree(html.substring(open, i + 1));
                }
            }
        }
        throw new IllegalStateException("Unterminated Stash blob in Jobindex HTML");
    }

    /** Recursively finds the node holding {@code searchResponse.results[]}. */
    private static JsonNode findSearchResults(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            JsonNode sr = node.path("searchResponse");
            if (sr.isObject() && sr.path("results").isArray()) return sr.path("results");
            for (JsonNode child : node) {
                JsonNode found = findSearchResults(child);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findSearchResults(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
