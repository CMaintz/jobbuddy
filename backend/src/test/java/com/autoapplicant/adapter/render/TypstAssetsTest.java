package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The bundled fonts are installed once and shared by every start, not copied per start. */
class TypstAssetsTest {

    /**
     * Bug caught: each start copies the fonts (about 16 MB) into a new directory that only a
     * graceful shutdown deletes; the desktop shell force-kills the backend, so every launch of
     * the app leaves one behind.
     */
    @Test
    void aSecondStartReusesTheInstalledFonts(@TempDir Path parent) throws IOException {
        TypstAssets first = new TypstAssets(parent);
        TypstAssets second = new TypstAssets(parent);

        assertThat(second.fontDir()).isEqualTo(first.fontDir());
        try (Stream<Path> entries = Files.list(parent)) {
            assertThat(entries).hasSize(1);
        }
        try (Stream<Path> fonts = Files.walk(first.fontDir())) {
            assertThat(fonts.filter(f -> f.toString().endsWith(".ttf"))).isNotEmpty();
        }
    }

    /** Bug caught: an install whose fonts were removed is trusted forever, so renders lose their fonts. */
    @Test
    void anUnfinishedInstallIsRedone(@TempDir Path parent) throws IOException {
        Path fontDir = new TypstAssets(parent).fontDir();
        Path home = fontDir.getParent();
        Files.delete(home.resolve(".installed"));
        try (Stream<Path> files = Files.walk(fontDir)) {
            for (Path f : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(f);
        }

        new TypstAssets(parent);

        assertThat(fontDir).isDirectory();
        assertThat(home.resolve(".installed")).isRegularFile();
    }
}
