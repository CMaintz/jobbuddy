package com.autoapplicant.adapter.pdf;

import com.autoapplicant.adapter.render.UploadsFiles;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.extend.FSUriResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF guard for PDF rendering. Templates carry user-authored HTML/CSS, and the renderer's
 * default user agent will happily fetch any http(s) or file URL they reference (cloud metadata
 * endpoints, internal hosts, local files) and embed the result. This policy allows only inline
 * {@code data:} URIs and the app's own uploads, which are read from local storage rather than
 * over HTTP. Every other reference resolves to nothing, so the renderer simply skips it.
 *
 * <p>It is installed twice. As the URI resolver it decides what a reference may point at. As the
 * stream factory for file, http and https it re-checks every load at the moment of opening, which
 * matters because openhtmltopdf's image loader skips the renderer's access-control hook. Any other
 * scheme would fall through to {@code URL.openStream}, so for those the resolver, which never
 * produces them, is the only gate.
 */
public final class PdfResourcePolicy implements FSUriResolver, FSStreamFactory {

    /** Schemes whose loads go through {@link #getUrl}; register the policy for each. */
    static final Set<String> GUARDED_SCHEMES = Set.of("file", "http", "https");

    private static final String DATA_SCHEME = "data:";

    private final UploadsFiles uploads;

    public PdfResourcePolicy() {
        this(new UploadsFiles());
    }

    PdfResourcePolicy(Path uploadsRoot) {
        this(new UploadsFiles(uploadsRoot));
    }

    private PdfResourcePolicy(UploadsFiles uploads) {
        this.uploads = uploads;
    }

    /**
     * Maps a template reference to a renderer URI, or {@code null} when it is not allowed. The
     * renderer resolves some references twice (a CSS {@code url()} is resolved by the parser and
     * again by the loader), so an already-resolved uploads {@code file:} URI must map to itself.
     */
    @Override
    public String resolveURI(String baseUri, String uri) {
        if (uri == null) return null;
        String trimmed = uri.trim();
        if (isDataUri(trimmed)) return trimmed;
        return uploads.resolve(trimmed).map(file -> file.toUri().toString()).orElse(null);
    }

    /** Opens a resolved URI for the renderer; anything outside uploads opens as nothing. */
    @Override
    public FSStream getUrl(String url) {
        return new UploadsFileStream(uploads.resolve(url).orElse(null));
    }

    private static boolean isDataUri(String uri) {
        return uri.toLowerCase(Locale.ROOT).startsWith(DATA_SCHEME);
    }

    /** One vetted uploads file; a refused reference ({@code null}) reads as nothing. */
    private record UploadsFileStream(Path file) implements FSStream {

        @Override
        public InputStream getStream() {
            if (file == null) return null;
            try {
                return Files.newInputStream(file);
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public Reader getReader() {
            InputStream stream = getStream();
            return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    }
}
