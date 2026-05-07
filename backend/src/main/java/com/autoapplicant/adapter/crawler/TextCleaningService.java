package com.autoapplicant.adapter.crawler;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Service
public class TextCleaningService {

    private static final int MAX_CHARS = 8000;

    public String clean(String rawHtml) {
        if (rawHtml == null) return null;
        String text = Jsoup.parse(rawHtml).text();
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
