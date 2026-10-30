package com.autoapplicant.adapter.render;

/**
 * Normalizes ATS-hostile Unicode in the PDF text layer to plain ASCII equivalents. Parsers read
 * the embedded text, not the rendered glyphs; smart quotes, en/em dashes, ellipses,
 * non-breaking/thin spaces and zero-width characters all degrade keyword extraction, and models
 * routinely emit them. The middot (U+00B7) the layouts use as a separator is left alone.
 */
public final class AtsText {

    private AtsText() {}

    public static String normalize(String value) {
        if (value == null) return null;
        return value
                .replace('\u2018', '\'').replace('\u2019', '\'') // smart single quotes
                .replace('\u201C', '"').replace('\u201D', '"') // smart double quotes
                .replace('\u2013', '-').replace('\u2014', '-') // en / em dash
                .replace('\u2212', '-') // minus sign
                .replace("\u2026", "...") // ellipsis
                .replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2009', ' ') // nbsp / narrow / thin
                .replace("\u200B", "").replace("\u200C", "") // zero-width space / non-joiner
                .replace("\u200D", "").replace("\uFEFF", ""); // zero-width joiner / BOM
    }
}
