package com.autoapplicant.adapter.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import com.autoapplicant.domain.job.JobText;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Service;

@Service
public class TextCleaningService {

    private static final int MAX_TITLE_CHARS = 120;

    /** Block-level tags whose boundaries are worth a line break in the extracted text. */
    private static final String BLOCK_TAGS =
            "p, div, li, tr, br, h1, h2, h3, h4, h5, h6, section, article, blockquote, pre";

    /**
     * Structural cleanup: markup and page chrome out, the posting's own line structure
     * kept. The line breaks matter twice over — a description renders as paragraphs
     * rather than one wall of text, and the enrichment pass addresses boilerplate by
     * line number, which only means anything if the lines survive.
     */
    public String clean(String rawHtml) {
        if (rawHtml == null) return null;
        Document doc = Jsoup.parse(rawHtml);
        // Strip page chrome so that full company job pages yield only relevant content
        doc.select("nav, header, footer, aside, script, style, noscript, iframe, " +
                   "[role=navigation], [role=banner], [role=contentinfo]").remove();
        return JobText.truncate(blockText(doc));
    }

    /**
     * Jsoup's own {@code text()} flattens everything to one line, and on a Document it
     * also pulls in the head's &lt;title&gt;, so the page title arrived glued to the first
     * line of the posting. Walk the body only, and keep block boundaries.
     */
    private static String blockText(Document doc) {
        StringBuilder sb = new StringBuilder();
        Element root = doc.body() != null ? doc.body() : doc;
        NodeTraversor.traverse(new NodeVisitor() {
            @Override public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    String text = textNode.text().replaceAll("[ \\t\\u00a0]+", " ");
                    if (!text.isBlank()) sb.append(text.strip()).append(' ');
                }
            }
            @Override public void tail(Node node, int depth) {
                if (node instanceof Element element && element.is(BLOCK_TAGS)
                        && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
            }
        }, root);

        // Collapse the runs of blank lines that empty wrapper elements leave behind.
        return sb.toString().replaceAll("[ \\t]*\\n[ \\t]*", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
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
