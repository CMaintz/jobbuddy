package com.autoapplicant.adapter.cli.rssinspection;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/** Fetches feeds and pages for the inspection report, plus its shared console formatting. */
final class RssFeedClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AutoApplicant-Bot/1.0; +https://autoapplicant.dk)";
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final String RULE = "=======================================================";

    Elements items(String rssUrl) throws IOException {
        String xml = Jsoup.connect(rssUrl).userAgent(USER_AGENT).timeout(CONNECT_TIMEOUT_MS).execute().body();
        return Jsoup.parse(xml, "", Parser.xmlParser()).select("item");
    }

    Element firstItem(String rssUrl) throws IOException {
        return items(rssUrl).first();
    }

    Element pageBody(String url) throws IOException {
        return Jsoup.connect(url).userAgent(USER_AGENT).timeout(CONNECT_TIMEOUT_MS).get().body();
    }

    static String baseUrl(String rawUrl) {
        return rawUrl.replaceAll("/$", "");
    }

    static String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    static void banner(String... lines) {
        System.out.println(RULE);
        for (String line : lines) {
            System.out.println("  " + line);
        }
        System.out.println(RULE);
        System.out.println();
    }
}
