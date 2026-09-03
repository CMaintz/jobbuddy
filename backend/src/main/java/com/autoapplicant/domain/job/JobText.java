package com.autoapplicant.domain.job;

import java.util.List;

/**
 * The one place the job-description size ceiling lives, and the line surgery the
 * enrichment pass uses to remove boilerplate.
 *
 * <p>The model is never asked to reproduce the posting — it points at the lines to
 * drop and the text is cut here. A posting the model rewrites is a paraphrase, and
 * everything downstream (matching, generation, the fact guard) treats this text as
 * the posting itself.
 */
public final class JobText {

    /**
     * Ceiling for a stored job description, applied once at ingestion. The enrichment
     * prompt sees the same text, so nothing is silently dropped between the two.
     */
    public static final int MAX_DESCRIPTION_CHARS = 8000;

    private JobText() {}

    public static String truncate(String text) {
        if (text == null) return null;
        return text.length() > MAX_DESCRIPTION_CHARS ? text.substring(0, MAX_DESCRIPTION_CHARS) : text;
    }

    /** The text with 1-based line numbers prefixed, as handed to the enrichment model. */
    public static String numbered(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length() + lines.length * 6);
        for (int i = 0; i < lines.length; i++) {
            sb.append(i + 1).append(": ").append(lines[i]).append('\n');
        }
        return sb.toString();
    }

    /**
     * Drops the given 1-based inclusive line ranges. Out-of-range, inverted and
     * overlapping ranges are tolerated rather than rejected: a model that miscounts
     * should cost us a surviving cookie banner, never the posting.
     */
    public static String stripLines(String text, List<int[]> ranges) {
        if (text == null || text.isBlank() || ranges == null || ranges.isEmpty()) return text;
        String[] lines = text.split("\n", -1);

        boolean[] drop = new boolean[lines.length];
        int kept = lines.length;
        for (int[] range : ranges) {
            if (range == null || range.length < 2) continue;
            int from = Math.max(1, Math.min(range[0], range[1]));
            int to = Math.min(lines.length, Math.max(range[0], range[1]));
            for (int i = from; i <= to; i++) {
                if (!drop[i - 1]) {
                    drop[i - 1] = true;
                    kept--;
                }
            }
        }
        // Refusing to gut the posting is the safer failure: a model that marks
        // everything has misunderstood the task, not found a page of boilerplate.
        if (kept == 0 || kept < lines.length / 4) return text;

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            if (drop[i]) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
