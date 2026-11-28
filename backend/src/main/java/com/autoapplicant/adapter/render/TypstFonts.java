package com.autoapplicant.adapter.render;

import java.util.Map;

/**
 * Maps a requested font to a family Typst has. Renders ignore system fonts so output is the same
 * on every machine, which means only the bundled faces exist. Every open font the app offers is
 * bundled under its own name; proprietary ones map to a metric-compatible open clone, and
 * anything else (including injected names) to the default.
 */
final class TypstFonts {

    static final String INTER = "Inter";
    static final String SANS = "Liberation Sans";
    static final String SERIF = "Liberation Serif";
    static final String MONO = "Liberation Mono";
    static final String CALIBRI = "Carlito";
    static final String GEORGIA = "Gelasio";

    private static final Map<String, String> BUNDLED = Map.ofEntries(
            Map.entry("Inter", INTER),
            Map.entry("Roboto", "Roboto"),
            Map.entry("Open Sans", "Open Sans"),
            Map.entry("Lato", "Lato"),
            Map.entry("Nunito", "Nunito"),
            Map.entry("Poppins", "Poppins"),
            Map.entry("Montserrat", "Montserrat"),
            Map.entry("Source Sans 3", "Source Sans 3"),
            Map.entry("Noto Sans", "Noto Sans"),
            Map.entry("JetBrains Mono", "JetBrains Mono"),
            Map.entry("Merriweather", "Merriweather"),
            Map.entry("Playfair Display", "Playfair Display"),
            Map.entry("Lora", "Lora"),
            Map.entry("Arial", SANS),
            Map.entry("Calibri", CALIBRI),
            Map.entry("Georgia", GEORGIA),
            Map.entry("Times New Roman", SERIF),
            Map.entry("Courier New", MONO),
            // No longer offered (Helvetica printed exactly like Arial, Khand has no italic), but
            // saved designs may still name them.
            Map.entry("Helvetica", SANS),
            Map.entry("Khand", INTER));

    private TypstFonts() {}

    static String bundled(String requested, String fallback) {
        return requested == null ? fallback : BUNDLED.getOrDefault(requested.trim(), fallback);
    }
}
