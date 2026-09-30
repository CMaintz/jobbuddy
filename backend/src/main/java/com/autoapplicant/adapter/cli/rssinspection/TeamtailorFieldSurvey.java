package com.autoapplicant.adapter.cli.rssinspection;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.nodes.Element;

/** Samples the first RSS item from a few Teamtailor companies and flags fields not all of them expose. */
final class TeamtailorFieldSurvey {

    private static final int SAMPLE_COUNT = 5;
    private static final int VALUE_PREVIEW = 80;
    private static final String STATUS_FIELD = "__status__";

    private final RssFeedClient feeds;

    TeamtailorFieldSurvey(RssFeedClient feeds) {
        this.feeds = feeds;
    }

    private record CompanySample(String label, Map<String, String> fields) {
        boolean has(String field) {
            return fields.containsKey(field);
        }
    }

    void print(List<String> rawUrls) {
        List<String> sampled = rawUrls.subList(0, Math.min(SAMPLE_COUNT, rawUrls.size()));
        RssFeedClient.banner(
                "Part 1 — Teamtailor RSS field inspection",
                "Sampling first item from " + sampled.size() + " companies");

        List<CompanySample> samples = sampled.stream()
                .map(RssFeedClient::baseUrl)
                .map(url -> new CompanySample(label(url), sampleFields(url)))
                .toList();
        samples.forEach(TeamtailorFieldSurvey::printSample);
        printInconsistentFields(samples);
    }

    private static String label(String baseUrl) {
        return baseUrl.replaceFirst("https://", "").replaceFirst("\\.teamtailor\\.com.*", "");
    }

    private Map<String, String> sampleFields(String baseUrl) {
        Map<String, String> fields = new LinkedHashMap<>();
        try {
            Element first = feeds.firstItem(baseUrl + "/jobs.rss");
            if (first == null) {
                fields.put(STATUS_FIELD, "(feed empty)");
                return fields;
            }
            for (Element child : first.children()) {
                fields.put(child.tagName(), RssFeedClient.truncate(child.text().trim(), VALUE_PREVIEW));
            }
        } catch (IOException | RuntimeException e) {
            fields.put(STATUS_FIELD, "(fetch failed: " + e.getMessage() + ")");
        }
        return fields;
    }

    private static void printSample(CompanySample sample) {
        System.out.println("  --- " + sample.label() + " ---");
        sample.fields().forEach((field, value) -> System.out.printf("    <%s>  %s%n", field, value));
        System.out.println();
    }

    private static void printInconsistentFields(List<CompanySample> samples) {
        Set<String> allFields = new LinkedHashSet<>();
        samples.forEach(s -> allFields.addAll(s.fields().keySet()));
        List<String> inconsistent = allFields.stream()
                .filter(field -> !samples.stream().allMatch(s -> s.has(field)))
                .toList();

        if (inconsistent.isEmpty()) {
            System.out.println("  All sampled companies expose the same fields.");
        } else {
            System.out.println("  Fields NOT present in all sampled companies:");
            inconsistent.forEach(field -> printPresence(field, samples));
        }
        System.out.println();
    }

    private static void printPresence(String field, List<CompanySample> samples) {
        Map<Boolean, List<String>> byPresence = samples.stream()
                .collect(Collectors.partitioningBy(
                        s -> s.has(field), Collectors.mapping(CompanySample::label, Collectors.toList())));
        System.out.printf("    <%s>  present in: %s  |  missing from: %s%n",
                field, byPresence.get(true), byPresence.get(false));
    }
}
