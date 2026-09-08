package com.autoapplicant.domain.job;

import java.util.Locale;

/**
 * 64-bit SimHash content fingerprint for near-duplicate job detection. Two postings
 * with the same (or near-identical) description text produce the same or a very close
 * fingerprint — catching agency re-posts of the same role under a different company or
 * URL, which source+id and URL dedup miss. Word-level (bag-of-words) so word order and
 * light reformatting don't change the hash.
 *
 * <p>Technique borrowed from an external reference implementation's {@code fingerprint-core.mjs}.
 */
public final class SimHash {

    private SimHash() {}

    /** Below this many usable tokens the fingerprint is unreliable; callers should skip. */
    public static final int MIN_TOKENS = 20;

    /** Returns the 64-bit SimHash of the text, or 0 when there is too little content. */
    public static long fingerprint(String text) {
        if (text == null || text.isBlank()) return 0L;
        String[] tokens = text.toLowerCase(Locale.ROOT).split("\\W+");
        int[] votes = new int[64];
        int used = 0;
        for (String token : tokens) {
            if (token.length() < 2) continue;
            long h = hash64(token);
            for (int i = 0; i < 64; i++) {
                if (((h >>> i) & 1L) == 1L) votes[i]++;
                else votes[i]--;
            }
            used++;
        }
        if (used < MIN_TOKENS) return 0L;
        long fp = 0L;
        for (int i = 0; i < 64; i++) {
            if (votes[i] > 0) fp |= (1L << i);
        }
        return fp;
    }

    /** Number of differing bits between two fingerprints (0 = identical content). */
    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /** FNV-1a 64-bit hash of a token. */
    private static long hash64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
