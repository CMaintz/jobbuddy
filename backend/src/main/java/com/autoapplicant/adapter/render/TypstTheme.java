package com.autoapplicant.adapter.render;

import com.autoapplicant.domain.document.structured.DocumentTheme;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The theme as the Typst template receives it. Every value is validated here, before it reaches
 * Typst: a malformed colour would abort the compile, and only bundled font families exist there.
 */
record TypstTheme(String primary, String accent, String font, double baseSize, double h1Size) {

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    /** The whitelisted theme fonts, each mapped to the bundled family that stands in for it. */
    private static final Map<String, String> BUNDLED_FAMILIES = Map.of(
            "Arial", "Liberation Sans",
            "Inter", "Inter",
            "Calibri", "Carlito",
            "Georgia", "Liberation Serif",
            "Times New Roman", "Liberation Serif");
    private static final String DEFAULT_FAMILY = "Liberation Sans";

    private static final Map<String, FontSizes> SCALES = Map.of(
            "small", new FontSizes(9.8, 21),
            "normal", new FontSizes(10.5, 24),
            "large", new FontSizes(11.2, 27));
    private static final String DEFAULT_SCALE = "normal";

    private record FontSizes(double base, double h1) {}

    static TypstTheme from(DocumentTheme theme) {
        DocumentTheme defaults = DocumentTheme.defaults();
        DocumentTheme given = theme != null ? theme : defaults;
        FontSizes sizes = SCALES.getOrDefault(given.fontScale(), SCALES.get(DEFAULT_SCALE));
        return new TypstTheme(
                colorOr(given.primaryColor(), defaults.primaryColor()),
                colorOr(given.accentColor(), defaults.accentColor()),
                given.fontFamily() == null
                        ? DEFAULT_FAMILY
                        : BUNDLED_FAMILIES.getOrDefault(given.fontFamily(), DEFAULT_FAMILY),
                sizes.base(),
                sizes.h1());
    }

    private static String colorOr(String value, String fallback) {
        return value != null && HEX_COLOR.matcher(value).matches() ? value : fallback;
    }
}
