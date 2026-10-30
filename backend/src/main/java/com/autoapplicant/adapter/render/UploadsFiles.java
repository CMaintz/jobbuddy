package com.autoapplicant.adapter.render;

import com.autoapplicant.config.UploadsLocation;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Decides which local files a renderer may read: only regular files inside the app's uploads
 * directory, named as {@code /uploads/...} or as a {@code file:} URI inside it. Everything else
 * (other local files, hosts, UNC shares, traversals, links out of uploads) resolves to nothing.
 */
public final class UploadsFiles {

    private static final String FILE_SCHEME = "file";
    private static final String UPLOADS_URL_PREFIX = UploadsLocation.URL_PREFIX;
    /** Windows reads a path starting with two separators as a UNC share on another host. */
    private static final String UNC_PATH_START = "//";
    private static final char WINDOWS_SEPARATOR = '\\';

    private final Path uploadsRoot;

    public UploadsFiles() {
        this(Path.of(UploadsLocation.DIRECTORY));
    }

    public UploadsFiles(Path uploadsRoot) {
        this.uploadsRoot = uploadsRoot.toAbsolutePath().normalize();
    }

    /** The real path of the uploads file a reference names, or empty for anything else. */
    public Optional<Path> resolve(String reference) {
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
}
