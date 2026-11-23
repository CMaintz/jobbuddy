package com.autoapplicant.adapter.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** The secret the desktop shell generates for one launch and presents on every request. */
public final class DesktopToken {

    /** Far beyond guessing for a random value, and short enough for any generator to produce. */
    static final int MIN_LENGTH = 32;

    private final byte[] digest;

    private DesktopToken(byte[] digest) {
        this.digest = digest;
    }

    /**
     * @throws IllegalStateException when the token is unset or shorter than {@value #MIN_LENGTH}
     *     characters. The app refuses to start rather than run with a secret anyone could match,
     *     since an empty token would otherwise accept an empty header.
     */
    public static DesktopToken of(String configured) {
        if (configured == null || configured.isBlank() || configured.length() < MIN_LENGTH) {
            throw new IllegalStateException("The desktop profile needs JOBBUDDY_DESKTOP_TOKEN set to"
                    + " a random secret of at least " + MIN_LENGTH + " characters");
        }
        return new DesktopToken(sha256(configured));
    }

    /** True only for the exact token, compared in constant time. */
    public boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        // Digests, not the raw strings: MessageDigest.isEqual is constant-time only for inputs of
        // equal length, so comparing raw values would leak the token's length.
        return MessageDigest.isEqual(digest, sha256(presented));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256", e);
        }
    }
}
