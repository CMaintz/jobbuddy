package com.autoapplicant.domain.document.structured;

import java.util.List;

/**
 * What the deterministic guards found in a generated document: metric claims with no support in
 * the profile, claims the user has explicitly disowned, and filler/AI-tell phrasing.
 *
 * <p>Carried into the {@link AtsReport} so the findings reach the person who can act on them.
 * They were previously warn-logged server-side only, which meant the user shipped the document
 * without ever seeing what the guards caught.
 */
public record ContentGuardFindings(
        List<String> unsupportedMetrics,
        List<String> retractedClaims,
        List<String> fillerPhrases,
        /**
         * Figures whose number is in the profile but attached to something else — usually the
         * model renaming what was counted. Worth showing, not worth failing on.
         */
        List<String> unverifiedMetrics) {

    public static final ContentGuardFindings NONE =
            new ContentGuardFindings(List.of(), List.of(), List.of(), List.of());

    public ContentGuardFindings {
        unsupportedMetrics = unsupportedMetrics != null ? List.copyOf(unsupportedMetrics) : List.of();
        retractedClaims = retractedClaims != null ? List.copyOf(retractedClaims) : List.of();
        fillerPhrases = fillerPhrases != null ? List.copyOf(fillerPhrases) : List.of();
        unverifiedMetrics = unverifiedMetrics != null ? List.copyOf(unverifiedMetrics) : List.of();
    }

    public boolean clean() {
        return unsupportedMetrics.isEmpty() && retractedClaims.isEmpty() && fillerPhrases.isEmpty()
                && unverifiedMetrics.isEmpty();
    }
}
