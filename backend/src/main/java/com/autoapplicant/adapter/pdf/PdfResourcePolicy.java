package com.autoapplicant.adapter.pdf;

import com.openhtmltopdf.extend.FSUriResolver;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceType;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * SSRF guard for PDF rendering. Templates carry user-authored HTML/CSS, and the renderer's
 * default user agent will happily fetch any http(s) or file URL they reference (cloud metadata
 * endpoints, internal hosts, local files) and embed the result. This policy allows only inline
 * {@code data:} URIs and the app's own uploads, which are read from local storage rather than
 * over HTTP. Every other reference resolves to nothing, so the renderer simply skips it.
 */
public final class PdfResourcePolicy implements FSUriResolver, BiPredicate<String, ExternalResourceType> {

    private static final String DATA_SCHEME = "data:";
    /** Path prefix the app serves uploads under (see {@code WebMvcConfig}). */
    private static final String UPLOADS_URL_PREFIX = "/uploads/";
    /** Local directory uploads are stored in, relative to the working directory. */
    private static final String UPLOADS_DIR = "uploads";

    private final Path uploadsRoot;

    public PdfResourcePolicy() {
        this(Path.of(UPLOADS_DIR));
    }

    PdfResourcePolicy(Path uploadsRoot) {
        this.uploadsRoot = uploadsRoot.toAbsolutePath().normalize();
    }

    /** Maps a template reference to a renderer URI, or {@code null} when it is not allowed. */
    @Override
    public String resolveURI(String baseUri, String uri) {
        if (uri == null) return null;
        String trimmed = uri.trim();
        if (isDataUri(trimmed)) return trimmed;
        if (!trimmed.startsWith(UPLOADS_URL_PREFIX)) return null;
        try {
            Path file = uploadsRoot.resolve(trimmed.substring(UPLOADS_URL_PREFIX.length())).normalize();
            return isInsideUploads(file) ? file.toUri().toString() : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** Access check on the already-resolved URI: a second gate in case resolution is bypassed. */
    @Override
    public boolean test(String resolvedUri, ExternalResourceType type) {
        if (resolvedUri == null) return false;
        if (isDataUri(resolvedUri)) return true;
        try {
            return isInsideUploads(Path.of(URI.create(resolvedUri)).normalize());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isDataUri(String uri) {
        return uri.toLowerCase(Locale.ROOT).startsWith(DATA_SCHEME);
    }

    private boolean isInsideUploads(Path file) {
        return file.startsWith(uploadsRoot) && !file.equals(uploadsRoot);
    }
}
