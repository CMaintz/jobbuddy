package com.autoapplicant.adapter.cli;

import com.autoapplicant.config.AppProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RSS field inspection and URL validation across Teamtailor, Jobindex, and IT-Jobbank.
 *
 * <ul>
 *   <li>Part 1 — Teamtailor: samples first item from up to 5 companies, cross-references
 *       field names to surface any inconsistencies in Teamtailor RSS implementations.</li>
 *   <li>Part 2 — Teamtailor URL validation: counts RSS jobs per configured URL and flags
 *       dead ones (fetch error) vs. valid feeds with 0 open positions.</li>
 *   <li>Part 3 — Jobindex: prints all fields from a sample item.</li>
 *   <li>Part 4 — IT-Jobbank: prints all fields from a sample item.</li>
 * </ul>
 *
 * <p>Run command (from repo root):
 * <pre>{@code
 *   $env:SPRING_PROFILES_ACTIVE="rss-test"; ./gradlew :backend:bootRun
 * }</pre>
 */
@Component
@Profile("rss-test")
public class TeamtailorRssQualityTest implements ApplicationRunner {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int RSS_PAGE_SIZE      = 100;
    private static final int TT_SAMPLE_COUNT    = 5;

    private final AppProperties appProperties;

    public TeamtailorRssQualityTest(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> ttUrls = appProperties.getTeamtailor().getCareerPageUrls();

        inspectTeamtailorFields(ttUrls);
        validateTeamtailorUrls(ttUrls);
        inspectFeed("Jobindex",   "https://www.jobindex.dk/jobsoegning.rss?page=1");
        inspectFeed("IT-Jobbank", "https://www.it-jobbank.dk/jobsoegning.rss?page=1");
    }

    // ── Part 1: Teamtailor field inspection across N companies ────────────────

    private void inspectTeamtailorFields(List<String> rawUrls) {
        int sampleSize = Math.min(TT_SAMPLE_COUNT, rawUrls.size());

        System.out.println("=======================================================");
        System.out.println("  Part 1 — Teamtailor RSS field inspection");
        System.out.println("  Sampling first item from " + sampleSize + " companies");
        System.out.println("=======================================================");
        System.out.println();

        // company label -> (field name -> value)
        List<String>              companyLabels = new ArrayList<>();
        List<Map<String, String>> companyFields = new ArrayList<>();

        for (int i = 0; i < sampleSize; i++) {
            String baseUrl = rawUrls.get(i).replaceAll("/$", "");
            String label   = baseUrl.replaceFirst("https://", "").replaceFirst("\\.teamtailor\\.com.*", "");
            companyLabels.add(label);

            Map<String, String> fields = new LinkedHashMap<>();
            try {
                String body = Jsoup.connect(baseUrl + "/jobs.rss")
                        .userAgent(USER_AGENT)
                        .timeout(CONNECT_TIMEOUT_MS)
                        .execute()
                        .body();
                Element first = Jsoup.parse(body, "", Parser.xmlParser()).select("item").first();
                if (first != null) {
                    for (Element child : first.children()) {
                        String text    = child.text().trim();
                        String display = text.length() > 80 ? text.substring(0, 80) + "…" : text;
                        fields.put(child.tagName(), display);
                    }
                } else {
                    fields.put("__status__", "(feed empty)");
                }
            } catch (Exception e) {
                fields.put("__status__", "(fetch failed: " + e.getMessage() + ")");
            }
            companyFields.add(fields);
        }

        // Collect all field names seen across all companies, preserving first-seen order
        Set<String> allFields = new LinkedHashSet<>();
        for (Map<String, String> fields : companyFields) allFields.addAll(fields.keySet());

        // Print per-company values
        for (int i = 0; i < companyLabels.size(); i++) {
            System.out.println("  --- " + companyLabels.get(i) + " ---");
            for (String field : companyFields.get(i).keySet()) {
                System.out.printf("    <%s>  %s%n", field, companyFields.get(i).get(field));
            }
            System.out.println();
        }

        // Cross-reference: flag fields that don't appear in every company
        Set<String> inconsistent = new LinkedHashSet<>();
        for (String field : allFields) {
            long presentIn = companyFields.stream().filter(m -> m.containsKey(field)).count();
            if (presentIn < companyLabels.size()) inconsistent.add(field);
        }

        if (inconsistent.isEmpty()) {
            System.out.println("  All sampled companies expose the same fields.");
        } else {
            System.out.println("  Fields NOT present in all sampled companies:");
            for (String field : inconsistent) {
                List<String> have    = new ArrayList<>();
                List<String> missing = new ArrayList<>();
                for (int i = 0; i < companyLabels.size(); i++) {
                    (companyFields.get(i).containsKey(field) ? have : missing).add(companyLabels.get(i));
                }
                System.out.printf("    <%s>  present in: %s  |  missing from: %s%n",
                        field, have, missing);
            }
        }
        System.out.println();
    }

    // ── Part 2: Teamtailor URL validation ─────────────────────────────────────

    private void validateTeamtailorUrls(List<String> rawUrls) {
        System.out.println("=======================================================");
        System.out.println("  Part 2 — Teamtailor URL validation (" + rawUrls.size() + " URLs)");
        System.out.println("=======================================================");
        System.out.println();

        int dead = 0;
        for (String rawUrl : rawUrls) {
            String baseUrl = rawUrl.replaceAll("/$", "");
            Result result  = countRssJobs(baseUrl);
            String status  = result.error()      ? "DEAD (fetch error — likely not on Teamtailor)"
                           : result.count() == 0 ? "0 jobs (valid feed, no open positions)"
                           : result.count() + " jobs";
            System.out.printf("  %-55s  %s%n", baseUrl, status);
            if (result.error()) dead++;
        }

        System.out.println();
        if (dead == 0) {
            System.out.println("  All URLs reachable.");
        } else {
            System.out.println("  " + dead + " URL(s) returned errors — remove them from application.yml.");
        }
        System.out.println();
    }

    private record Result(int count, boolean error) {}

    private Result countRssJobs(String baseUrl) {
        int count = 0;
        for (int offset = 0; ; offset += RSS_PAGE_SIZE) {
            String rssUrl = baseUrl + "/jobs.rss?offset=" + offset + "&per_page=" + RSS_PAGE_SIZE;
            try {
                String body = Jsoup.connect(rssUrl)
                        .userAgent(USER_AGENT)
                        .timeout(CONNECT_TIMEOUT_MS)
                        .execute()
                        .body();
                Elements items = Jsoup.parse(body, "", Parser.xmlParser()).select("item");
                count += items.size();
                if (items.size() < RSS_PAGE_SIZE) break;
            } catch (Exception e) {
                if (count == 0) return new Result(0, true);
                break;
            }
        }
        return new Result(count, false);
    }

    // ── Parts 3 & 4: Jobindex and IT-Jobbank ─────────────────────────────────

    private void inspectFeed(String name, String rssUrl) {
        System.out.println("=======================================================");
        System.out.println("  " + name + " — RSS fields + detail page structure");
        System.out.println("  RSS: " + rssUrl);
        System.out.println("=======================================================");
        System.out.println();

        String detailUrl = null;
        try {
            String rssBody = Jsoup.connect(rssUrl)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .execute()
                    .body();
            Element firstItem = Jsoup.parse(rssBody, "", Parser.xmlParser()).select("item").first();
            if (firstItem == null) {
                System.out.println("  No items found in RSS.");
                System.out.println();
                return;
            }

            System.out.println("  RSS fields:");
            for (Element child : firstItem.children()) {
                String text    = child.text().trim();
                String display = text.length() > 120 ? text.substring(0, 120) + "…" : text;
                System.out.printf("    <%s>%s  %s%n",
                        child.tagName(),
                        child.attributes().size() > 0 ? " " + child.attributes() : "",
                        display);
            }

            // Resolve detail URL from <link> or <guid>
            detailUrl = firstItem.select("link").text().trim();
            if (detailUrl.isBlank()) {
                String guid = firstItem.select("guid").text().trim();
                if (guid.startsWith("http")) detailUrl = guid;
            }
        } catch (Exception e) {
            System.out.println("  RSS fetch failed: " + e.getMessage());
        }

        System.out.println();

        if (detailUrl == null || detailUrl.isBlank()) {
            System.out.println("  Could not resolve detail URL.");
            System.out.println();
            return;
        }

        System.out.println("  Detail page structure: " + detailUrl);
        System.out.println("  (showing tag + #id + .classes, depth ≤ 4)");
        System.out.println();
        try {
            Element body = Jsoup.connect(detailUrl)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .get()
                    .body();
            printStructure(body, 0, 4);
        } catch (Exception e) {
            System.out.println("  Detail page fetch failed: " + e.getMessage());
        }

        System.out.println();
    }

    private void printStructure(Element el, int depth, int maxDepth) {
        if (depth > maxDepth) return;
        String indent  = "  ".repeat(depth + 2);
        String id      = el.id().isBlank()       ? "" : "#" + el.id();
        String cls     = el.className().isBlank() ? "" : "." + el.className().trim().replace(" ", ".");
        String preview = el.ownText().trim();
        if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
        String line = indent + el.tagName() + id + cls;
        if (!preview.isBlank()) line += "  \"" + preview + "\"";
        System.out.println(line);
        for (Element child : el.children()) {
            printStructure(child, depth + 1, maxDepth);
        }
    }
}
