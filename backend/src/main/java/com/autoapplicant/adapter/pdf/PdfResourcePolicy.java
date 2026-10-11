package com.autoapplicant.adapter.pdf;

import com.autoapplicant.config.UploadsLocation;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.extend.FSUriResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
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
    private static final String FILE_SCHEME = "file";
    private static final String UPLOADS_URL_PREFIX = UploadsLocation.URL_PREFIX;
    /** Windows reads a path starting with two separators as a UNC share on another host. */
    private static final String UNC_PATH_START = "//";
    private static final char WINDOWS_SEPARATOR = '\\';

    private final Path uploadsRoot;

    public PdfResourcePolicy() {
        this(Path.of(UploadsLocation.DIRECTORY));
    }

    PdfResourcePolicy(Path uploadsRoot) {
        this.uploadsRoot = uploadsRoot.toAbsolutePath().normalize();
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
        return uploadsFile(trimmed).map(file -> file.toUri().toString()).orElse(null);
    }

    /** Opens a resolved URI for the renderer; anything outside uploads opens as nothing. */
    @Override
    public FSStream getUrl(String url) {
        return new UploadsFileStream(uploadsFile(url).orElse(null));
    }

    /** The real path of the uploads file a reference names, or empty for anything else. */
    private Optional<Path> uploadsFile(String reference) {
        Optional<Path> candidate = reference.startsWith(UPLOADS_URL_PREFIX)
                ? underUploads(reference.substring(UPLOADS_URL_PREFIX.length()))
                : fromFileUri(reference);
        return candidate.flatMap(this::realPathInsideUploads);
    }

    /** A path under uploads; a backslash or rooted remainder (drive, UNC share) is refused. */
    private Optional<Path> underUploads(String relative) {
        if (relative.indexOf(WINDOWS_SEPARATOR) >= 0) return Optional.empty();
        try {
            Path path = Path.of(relative);
            return path.getRoot() == null ? Optional.of(uploadsRoot.resolve(path)) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** A local {@code file:} URI as a path; one naming a host or a UNC share is refused. */
    private static Optional<Path> fromFileUri(String reference) {
        try {
            URI uri = new URI(reference);
            if (!FILE_SCHEME.equalsIgnoreCase(uri.getScheme()) || isRemote(uri)) return Optional.empty();
            return Optional.of(Path.of(uri));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean isRemote(URI uri) {
        String path = uri.getRawPath();
        return uri.getRawAuthority() != null || path == null || path.startsWith(UNC_PATH_START);
    }

    /**
     * Resolves {@code ..} and symlinks on both sides before comparing, so neither a traversal
     * nor a link inside uploads pointing elsewhere gets out. A missing file is refused too. The
     * candidate must pass a lexical check before it is touched: {@code toRealPath} hits the
     * filesystem, and on Windows touching a UNC path is an SMB call that can leak the server's
     * NTLM credentials. Both forms of the root are accepted because a resolved URI already
     * carries the real path (e.g. macOS {@code /private/var} for {@code /var}).
     */
    private Optional<Path> realPathInsideUploads(Path candidate) {
        try {
            Path realRoot = uploadsRoot.toRealPath();
            Path lexical = candidate.normalize();
            if (!lexical.startsWith(uploadsRoot) && !lexical.startsWith(realRoot)) {
                return Optional.empty();
            }
            Path real = candidate.toRealPath();
            boolean inside = real.startsWith(realRoot) && Files.isRegularFile(real);
            return inside ? Optional.of(real) : Optional.empty();
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
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
