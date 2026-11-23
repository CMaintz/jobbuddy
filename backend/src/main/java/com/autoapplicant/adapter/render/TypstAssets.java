package com.autoapplicant.adapter.render;

import com.autoapplicant.config.AppDataDirectory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * The files every render shares: the bundled fonts, the built-in template and the package
 * directories. Typst reads fonts and packages from disk, and inside the boot jar they are not
 * files, so they are copied out into the app data directory, once per set of fonts. A start
 * reuses what an earlier one installed: the desktop shell stops the backend with a forced kill,
 * so nothing installed per start would ever be cleaned up.
 *
 * <p>Both package directories are bundled and start empty. A template can only import a package
 * someone placed there; Typst would download anything else, which {@link TypstCommand} prevents.
 */
@Component
public final class TypstAssets {

    private static final String BASE = "typst/";
    private static final String FONTS = BASE + "fonts/";
    private static final String FONT_PATTERN = "classpath*:" + FONTS + "**/*.*";
    static final String TEMPLATE = BASE + "structured-document.typ";

    private static final String INSTALLED_MARKER = ".installed";
    private static final int FINGERPRINT_HEX_CHARS = 16;

    private final Path home;
    private final String template;

    @Autowired
    public TypstAssets(AppDataDirectory dataDirectory) throws IOException {
        this(dataDirectory.root().resolve("typst-assets"));
    }

    /** @param parent where each set of fonts gets its own directory, named by its content */
    TypstAssets(Path parent) throws IOException {
        Resource[] fonts = bundledFonts();
        // Typst runs inside each render's workspace, so a relative path would point nowhere and
        // it would silently fall back to its own built-in fonts.
        this.home = parent.toAbsolutePath().normalize().resolve(fingerprint(fonts));
        if (!Files.isRegularFile(home.resolve(INSTALLED_MARKER))) install(fonts, parent);
        Files.createDirectories(packageDir());
        Files.createDirectories(packageCacheDir());
        this.template = readTemplate();
    }

    Path fontDir() {
        return home.resolve("fonts");
    }

    Path packageDir() {
        return home.resolve("packages");
    }

    Path packageCacheDir() {
        return home.resolve("package-cache");
    }

    /** The source of the built-in structured-document template. */
    String template() {
        return template;
    }

    /**
     * Copies the fonts into a scratch directory and moves it into place in one step, so a
     * process that dies halfway, or a second backend starting at the same time, never leaves a
     * directory that looks installed but is missing fonts.
     */
    private void install(Resource[] fonts, Path parent) throws IOException {
        Files.createDirectories(parent);
        // Without the marker the directory was damaged after installing; it cannot be trusted.
        deleteTree(home);
        Path scratch = Files.createTempDirectory(parent, "installing-");
        try {
            copyFonts(fonts, scratch.resolve("fonts"));
            Files.createFile(scratch.resolve(INSTALLED_MARKER));
            moveIntoPlace(scratch);
        } finally {
            deleteTree(scratch);
        }
    }

    private void moveIntoPlace(Path scratch) throws IOException {
        try {
            Files.move(scratch, home, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Another backend installed the same fonts first; on Windows that shows up as an
            // access-denied move rather than an existing target, so check the result instead.
            if (!Files.isRegularFile(home.resolve(INSTALLED_MARKER))) throw e;
        }
    }

    private static Resource[] bundledFonts() throws IOException {
        Resource[] fonts = new PathMatchingResourcePatternResolver().getResources(FONT_PATTERN);
        // With system fonts ignored, a scan that finds nothing would silently fall back to
        // Typst's built-in faces; fail at startup instead.
        if (fonts.length == 0) throw new IllegalStateException("No bundled fonts under " + FONTS);
        return fonts;
    }

    /** Changes whenever a bundled font is added, removed or replaced, so an upgrade reinstalls. */
    private static String fingerprint(Resource[] fonts) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Resource font : sortedByPath(fonts)) {
                digest.update(relativePath(font).getBytes(StandardCharsets.UTF_8));
                try (InputStream in = font.getInputStream()) {
                    digest.update(in.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, FINGERPRINT_HEX_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static Resource[] sortedByPath(Resource[] fonts) {
        Resource[] sorted = fonts.clone();
        Arrays.sort(sorted, Comparator.comparing(TypstAssets::relativePathUnchecked));
        return sorted;
    }

    private static void copyFonts(Resource[] fonts, Path target) throws IOException {
        for (Resource font : fonts) {
            Path file = target.resolve(relativePath(font));
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            try (InputStream in = font.getInputStream()) {
                Files.copy(in, file);
            }
        }
    }

    private static String relativePath(Resource font) throws IOException {
        String uri = font.getURI().toString();
        return uri.substring(uri.lastIndexOf(FONTS) + FONTS.length());
    }

    private static String relativePathUnchecked(Resource font) {
        try {
            return relativePath(font);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static String readTemplate() {
        try (InputStream in = TypstAssets.class.getClassLoader().getResourceAsStream(TEMPLATE)) {
            if (in == null) throw new IllegalStateException("Missing Typst template " + TEMPLATE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
