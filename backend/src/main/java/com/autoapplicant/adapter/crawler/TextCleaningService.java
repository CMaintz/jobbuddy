package com.autoapplicant.adapter.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Service
public class TextCleaningService {

    private static final int MAX_CHARS = 8000;
    private static final int MAX_TITLE_CHARS = 120;

    public String clean(String rawHtml) {
        if (rawHtml == null) return null;
        String text = Jsoup.parse(rawHtml).text();
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    /**
     * Extracts a human-readable job title from raw HTML.
     * Strategy: <title> tag → <h1> → first non-trivial sentence of body text.
     */
    public String extractTitle(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return "Untitled";

        Document doc = Jsoup.parse(rawHtml);

        // 1. <title> tag — strip common suffixes like " | CompanyName" or " - JobBoard"
        String pageTitle = doc.title().trim();
        if (!pageTitle.isBlank()) {
            String cleaned = stripSiteSuffix(pageTitle);
            if (!cleaned.isBlank()) return truncate(cleaned);
        }

        // 2. First <h1>
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            String text = h1.text().trim();
            if (!text.isBlank()) return truncate(text);
        }

        // 3. First meaningful sentence from body text
        String bodyText = doc.body() != null ? doc.body().text() : doc.text();
        bodyText = bodyText.replaceAll("\\s+", " ").trim();
        if (!bodyText.isBlank()) {
            int end = bodyText.indexOf(". ");
            String sentence = end > 10 && end < MAX_TITLE_CHARS
                    ? bodyText.substring(0, end)
                    : bodyText;
            return truncate(sentence);
        }

        return "Untitled";
    }

    /** Strips " | Suffix" or " - Suffix" or " — Suffix" trailing site names. */
    private String stripSiteSuffix(String title) {
        return title.replaceAll("\\s*[|\\-\u2014].*$", "").trim();
    }

    private String truncate(String s) {
        return s.length() > MAX_TITLE_CHARS ? s.substring(0, MAX_TITLE_CHARS).trim() : s;
    }
}
