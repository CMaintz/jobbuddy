package com.autoapplicant.adapter.render;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Identifies an image by its leading bytes, never by the name or MIME type it arrived with.
 * Typst aborts the whole compile on an image it cannot decode, and uploads are stored under an
 * extension that does not prove their content.
 */
final class ImageFormat {

    /** How far into a file to look for the {@code <svg} root, past an XML prolog or comments. */
    private static final int SVG_SNIFF_LENGTH = 1024;
    private static final String SVG_ROOT = "<svg";
    private static final int WEBP_TAG_OFFSET = 8;

    /** One or more byte markers that must all match for the format to apply. */
    private record Signature(String extension, List<Marker> markers) {
        boolean matches(byte[] bytes) {
            return markers.stream().allMatch(marker -> marker.matches(bytes));
        }
    }

    private record Marker(int offset, byte[] expected) {
        boolean matches(byte[] bytes) {
            int end = offset + expected.length;
            return bytes.length >= end && Arrays.equals(bytes, offset, end, expected, 0, expected.length);
        }
    }

    private static final List<Signature> SIGNATURES = List.of(
            new Signature("png", List.of(new Marker(0,
                    new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}))),
            new Signature("jpg", List.of(new Marker(0, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))),
            new Signature("gif", List.of(new Marker(0, ascii("GIF8")))),
            new Signature("webp", List.of(new Marker(0, ascii("RIFF")), new Marker(WEBP_TAG_OFFSET, ascii("WEBP")))));

    private ImageFormat() {}

    /** The file extension Typst needs for these bytes, or empty when it cannot decode them. */
    static Optional<String> extensionOf(byte[] bytes) {
        Optional<String> binary = SIGNATURES.stream()
                .filter(signature -> signature.matches(bytes))
                .map(Signature::extension)
                .findFirst();
        return binary.isPresent() || !isSvg(bytes) ? binary : Optional.of("svg");
    }

    private static boolean isSvg(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, SVG_SNIFF_LENGTH), StandardCharsets.UTF_8);
        return head.contains(SVG_ROOT);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
