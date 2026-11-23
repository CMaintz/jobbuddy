package com.autoapplicant.adapter.crawler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/** Loads saved source documents from {@code src/test/resources/crawler/}. */
final class CrawlerFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CrawlerFixtures() {}

    static String read(String path) {
        try (InputStream in = CrawlerFixtures.class.getResourceAsStream("/crawler/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture /crawler/" + path + " on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode json(String path) {
        return parseJson(read(path));
    }

    static JsonNode parseJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Document html(String path) {
        return Jsoup.parse(read(path));
    }

    /** Parses an RSS feed the way the connectors do, with Jsoup's XML parser. */
    static Elements rssItems(String path) {
        return Jsoup.parse(read(path), "", Parser.xmlParser()).select("item");
    }
}
