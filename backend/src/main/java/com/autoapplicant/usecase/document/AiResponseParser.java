package com.autoapplicant.usecase.document;

/** Utility for cleaning raw AI text responses before JSON parsing. */
public final class AiResponseParser {

    private AiResponseParser() {}

    public static String extractJsonObject(String value) {
        String json = stripCodeFence(value);
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("AI response was not a JSON object");
        }
        return json.substring(first, last + 1).trim();
    }

    /** Replaces em/en dashes with regular hyphens — house style for all AI output. */
    public static String sanitize(String text) {
        if (text == null) return null;
        return text.replace('—', '-')   // em dash
                   .replace('–', '-');   // en dash
    }

    public static String stripCodeFence(String value) {
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                return value.substring(firstLine + 1, lastFence).trim();
            }
        }
        return value;
    }
}
