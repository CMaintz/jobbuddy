package com.autoapplicant.adapter.render;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * The files every render shares: the bundled fonts, the built-in template and the package
 * directories. Typst reads fonts and packages from disk, and inside the boot jar they are not
 * files, so they are copied out once at startup into a private directory.
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

    private final Path home;
    private final String template;

    public TypstAssets() throws IOException {
        this.home = Files.createTempDirectory("typst-assets-");
        copyFonts(home.resolve("fonts"));
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

    @PreDestroy
    void delete() throws IOException {
        try (Stream<Path> files = Files.walk(home)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static void copyFonts(Path target) throws IOException {
        Resource[] fonts = new PathMatchingResourcePatternResolver().getResources(FONT_PATTERN);
        // With system fonts ignored, a scan that finds nothing would silently fall back to
        // Typst's built-in faces; fail at startup instead.
        if (fonts.length == 0) throw new IllegalStateException("No bundled fonts under " + FONTS);
        for (Resource font : fonts) {
            String uri = font.getURI().toString();
            Path file = target.resolve(uri.substring(uri.lastIndexOf(FONTS) + FONTS.length()));
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            try (InputStream in = font.getInputStream()) {
                Files.copy(in, file);
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
