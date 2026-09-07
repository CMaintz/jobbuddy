package com.autoapplicant.domain.document.structured;

import java.util.List;

/**
 * How much of what the posting asked for the document actually says, measured against
 * the document text rather than reported by the model that wrote it.
 *
 * <p>{@code measured} is false when the posting carried no keywords to check — an
 * unenriched job, say. That is different from scoring zero, and the report says so
 * rather than showing a damning 0%.
 */
public record KeywordCoverage(
        boolean measured,
        int percent,
        List<String> matched,
        List<String> missing,
        /** The subset of {@link #missing} the posting listed as a requirement. */
        List<String> missingRequired
) {
    public static final KeywordCoverage NOT_MEASURED =
            new KeywordCoverage(false, 0, List.of(), List.of(), List.of());
}
