package com.autoapplicant.adapter.cli.rssinspection;

import java.io.IOException;
import java.util.Optional;
import org.jsoup.nodes.Element;

/** Prints one RSS item's fields and the element structure of the job page it links to. */
final class FeedStructureInspector {

    private static final int VALUE_PREVIEW = 120;
    private static final int TEXT_PREVIEW = 60;
    private static final int MAX_DEPTH = 4;
    private static final int BASE_INDENT = 2;

    private final RssFeedClient feeds;

    FeedStructureInspector(RssFeedClient feeds) {
        this.feeds = feeds;
    }

    void print(String name, String rssUrl) {
        RssFeedClient.banner(name + " — RSS fields + detail page structure", "RSS: " + rssUrl);
        Optional<String> detailUrl = printFirstItem(rssUrl);
        System.out.println();
        if (detailUrl.isPresent()) {
            printDetailPage(detailUrl.get());
        } else {
            System.out.println("  Could not resolve detail URL.");
            System.out.println();
        }
    }

    private Optional<String> printFirstItem(String rssUrl) {
        try {
            Element item = feeds.firstItem(rssUrl);
            if (item == null) {
                System.out.println("  No items found in RSS.");
                return Optional.empty();
            }
            printFields(item);
            return detailUrl(item);
        } catch (IOException | RuntimeException e) {
            System.out.println("  RSS fetch failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static void printFields(Element item) {
        System.out.println("  RSS fields:");
        for (Element child : item.children()) {
            String attributes = child.attributes().size() > 0 ? " " + child.attributes() : "";
            String value = RssFeedClient.truncate(child.text().trim(), VALUE_PREVIEW);
            System.out.printf("    <%s>%s  %s%n", child.tagName(), attributes, value);
        }
    }

    private static Optional<String> detailUrl(Element item) {
        String link = item.select("link").text().trim();
        if (!link.isBlank()) {
            return Optional.of(link);
        }
        String guid = item.select("guid").text().trim();
        return guid.startsWith("http") ? Optional.of(guid) : Optional.empty();
    }

    private void printDetailPage(String url) {
        System.out.println("  Detail page structure: " + url);
        System.out.println("  (showing tag + #id + .classes, depth ≤ " + MAX_DEPTH + ")");
        System.out.println();
        try {
            printStructure(feeds.pageBody(url), 0);
        } catch (IOException | RuntimeException e) {
            System.out.println("  Detail page fetch failed: " + e.getMessage());
        }
        System.out.println();
    }

    private static void printStructure(Element element, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        System.out.println("  ".repeat(depth + BASE_INDENT) + describe(element));
        for (Element child : element.children()) {
            printStructure(child, depth + 1);
        }
    }

    private static String describe(Element element) {
        String id = element.id().isBlank() ? "" : "#" + element.id();
        String classes = element.className().isBlank() ? "" : "." + element.className().trim().replace(" ", ".");
        String preview = RssFeedClient.truncate(element.ownText().trim(), TEXT_PREVIEW);
        return element.tagName() + id + classes + (preview.isBlank() ? "" : "  \"" + preview + "\"");
    }
}
