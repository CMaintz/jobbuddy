package com.autoapplicant.adapter.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Copies a document image into the workspace root, where Typst can read it. Typst never fetches
 * anything itself, so this is the whole SSRF boundary: only an inline {@code data:} image or a
 * file from the app's own uploads ({@link UploadsFiles}) is staged. Anything else (a remote URL,
 * a local file outside uploads, bytes in no format Typst decodes) is dropped, and the layout
 * shows initials instead.
 */
@Component
public class TypstImageStager {

    private static final String DATA_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final String STAGED_NAME = "image.";

    private final UploadsFiles uploads;

    public TypstImageStager() {
        this(new UploadsFiles());
    }

    TypstImageStager(UploadsFiles uploads) {
        this.uploads = uploads;
    }

    /** The staged file's name inside {@code root}, or empty when the reference is not allowed. */
    Optional<String> stage(String reference, Path root) throws IOException {
        Optional<byte[]> bytes = read(reference);
        Optional<String> extension = bytes.flatMap(ImageFormat::extensionOf);
        if (extension.isEmpty()) return Optional.empty();
        String name = STAGED_NAME + extension.get();
        Files.write(root.resolve(name), bytes.get());
        return Optional.of(name);
    }

    private Optional<byte[]> read(String reference) throws IOException {
        if (reference == null || reference.isBlank()) return Optional.empty();
        String trimmed = reference.trim();
        if (trimmed.regionMatches(true, 0, DATA_PREFIX, 0, DATA_PREFIX.length())) {
            return decodeDataUri(trimmed);
        }
        Optional<Path> upload = uploads.resolve(trimmed);
        return upload.isPresent() ? Optional.of(Files.readAllBytes(upload.get())) : Optional.empty();
    }

    private static Optional<byte[]> decodeDataUri(String uri) {
        int marker = uri.indexOf(BASE64_MARKER);
        if (marker < 0) return Optional.empty();
        try {
            return Optional.of(Base64.getMimeDecoder().decode(uri.substring(marker + BASE64_MARKER.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
