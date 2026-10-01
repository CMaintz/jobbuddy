package com.autoapplicant.adapter.cli.rssinspection;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;

/** Counts the open jobs behind each configured Teamtailor career page and flags dead URLs. */
final class TeamtailorUrlCheck {

    private static final int PAGE_SIZE = 100;

    private final RssFeedClient feeds;

    TeamtailorUrlCheck(RssFeedClient feeds) {
        this.feeds = feeds;
    }

    void print(List<String> rawUrls) {
        RssFeedClient.banner("Part 2 — Teamtailor URL validation (" + rawUrls.size() + " URLs)");

        long dead = rawUrls.stream()
                .map(RssFeedClient::baseUrl)
                .map(this::checkAndPrint)
                .filter(OptionalInt::isEmpty)
                .count();

        System.out.println();
        System.out.println(dead == 0
                ? "  All URLs reachable."
                : "  " + dead + " URL(s) returned errors — remove them from application.yml.");
        System.out.println();
    }

    private OptionalInt checkAndPrint(String baseUrl) {
        OptionalInt jobs = countJobs(baseUrl);
        System.out.printf("  %-55s  %s%n", baseUrl, describe(jobs));
        return jobs;
    }

    private static String describe(OptionalInt jobs) {
        if (jobs.isEmpty()) {
            return "DEAD (fetch error — likely not on Teamtailor)";
        }
        return jobs.getAsInt() == 0 ? "0 jobs (valid feed, no open positions)" : jobs.getAsInt() + " jobs";
    }

    /** Pages through the feed; empty only when the very first page cannot be fetched. */
    private OptionalInt countJobs(String baseUrl) {
        int total = 0;
        for (int offset = 0; ; offset += PAGE_SIZE) {
            OptionalInt page = pageSize(baseUrl, offset);
            if (page.isEmpty()) {
                return total == 0 ? OptionalInt.empty() : OptionalInt.of(total);
            }
            total += page.getAsInt();
            if (page.getAsInt() < PAGE_SIZE) {
                return OptionalInt.of(total);
            }
        }
    }

    private OptionalInt pageSize(String baseUrl, int offset) {
        try {
            String url = baseUrl + "/jobs.rss?offset=" + offset + "&per_page=" + PAGE_SIZE;
            return OptionalInt.of(feeds.items(url).size());
        } catch (IOException | RuntimeException e) {
            return OptionalInt.empty();
        }
    }
}
