package com.autoapplicant.usecase.document;

import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;

/**
 * A letter body as the gate reads it. After an edit in the browser the stored body is the editor's
 * HTML; judged as-is, the gate quotes tags and entities back, and its per-paragraph count (point 14)
 * no longer sees the paragraphs.
 */
final class LetterBodyText {

    private static final Pattern PARAGRAPH_BREAKS = Pattern.compile("\\s*\\n\\s*\\n\\s*");

    private LetterBodyText() {
    }

    /** Plain text with one blank line between paragraphs; a body without markup is returned as is. */
    static String plain(String body) {
        if (body == null || body.indexOf('<') < 0) {
            return body;
        }
        Document html = Jsoup.parseBodyFragment(body);
        html.select("br").forEach(br -> br.after(new TextNode("\n")));
        html.select("p, li, div, h1, h2, h3, h4, h5, h6").forEach(block -> block.after(new TextNode("\n\n")));
        return PARAGRAPH_BREAKS.matcher(html.body().wholeText()).replaceAll("\n\n").strip();
    }
}
