package com.autoapplicant.adapter.render;

import java.util.Map;

/**
 * Maps a requested font to a family Typst has. Renders ignore system fonts so output is the same
 * on every machine, which means only the bundled faces exist; every font the app offers maps to
 * the closest of them, and anything else (including injected names) to the default.
 */
final class TypstFonts {

    static final String INTER = "Inter";
    static final String SANS = "Liberation Sans";
    static final String SERIF = "Liberation Serif";
    static final String CALIBRI = "Carlito";

    private static final Map<String, String> BUNDLED = Map.ofEntries(
            Map.entry("Inter", INTER),
            Map.entry("Roboto", INTER),
            Map.entry("Open Sans", INTER),
            Map.entry("Lato", INTER),
            Map.entry("Nunito", INTER),
            Map.entry("Poppins", INTER),
            Map.entry("Montserrat", INTER),
            Map.entry("Source Sans 3", INTER),
            Map.entry("Noto Sans", INTER),
            Map.entry("Khand", INTER),
            Map.entry("JetBrains Mono", INTER),
            Map.entry("Courier New", INTER),
            Map.entry("Arial", SANS),
            Map.entry("Helvetica", SANS),
            Map.entry("Calibri", CALIBRI),
            Map.entry("Georgia", SERIF),
            Map.entry("Times New Roman", SERIF),
            Map.entry("Merriweather", SERIF),
            Map.entry("Playfair Display", SERIF),
            Map.entry("Lora", SERIF));

    private TypstFonts() {}

    static String bundled(String requested, String fallback) {
        return requested == null ? fallback : BUNDLED.getOrDefault(requested.trim(), fallback);
    }
}
