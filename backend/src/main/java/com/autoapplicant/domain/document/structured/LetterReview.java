package com.autoapplicant.domain.document.structured;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * What a generated letter was written from and how the gate judged it, stored with the document.
 *
 * @param needs the employer needs the letter was written from, shown to the user
 * @param gate the gate's verdict on the letter text it was given; null for a letter with no posting
 *     to gate against, which still shows its needs
 * @param gatedBodyFingerprint fingerprint of that letter text, so a later edit shows as stale
 * @param stale true once the letter has changed since the gate judged it
 */
public record LetterReview(List<String> needs, LetterGateResult gate, String gatedBodyFingerprint,
                           boolean stale) {

    public LetterReview {
        needs = needs != null ? List.copyOf(needs) : List.of();
    }

    /** A review of {@code body} as the gate saw it, or with no verdict when {@code gate} is null. */
    public static LetterReview of(List<String> needs, LetterGateResult gate, String body) {
        return new LetterReview(needs, gate, fingerprint(body), false);
    }

    /** This review, marked stale when {@code body} is no longer the text the gate judged. */
    public LetterReview checkedAgainst(String body) {
        if (stale || fingerprint(body).equals(gatedBodyFingerprint)) {
            return this;
        }
        return new LetterReview(needs, gate, gatedBodyFingerprint, true);
    }

    /** SHA-256 of the text with surrounding whitespace removed. */
    public static String fingerprint(String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((body != null ? body.strip() : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
