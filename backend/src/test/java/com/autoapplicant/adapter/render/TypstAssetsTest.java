package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.config.AppDataDirectory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The bundled fonts are installed once and shared by every start, not copied per start, and a
 * start removes the copies an earlier font set left behind.
 */
class TypstAssetsTest {

    private static final String OLD_COPY = "0123456789abcdef";
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

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

    /**
     * Bug caught: a data directory the backend cannot write (the Docker image's /app belongs to
     * root) makes the font install throw, and the app fails to start.
     */
    @Test
    void anUnwritableDataDirectoryFallsBackToTheTempDirectory(@TempDir Path dataDir) throws IOException {
        Files.writeString(dataDir.resolve("typst-assets"), "a file where the directory should be");

        TypstAssets assets = new TypstAssets(new AppDataDirectory(dataDir.toString()));

        assertThat(assets.fontDir()).startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath());
        try (Stream<Path> fonts = Files.walk(assets.fontDir())) {
            assertThat(fonts.filter(f -> f.toString().endsWith(".ttf"))).isNotEmpty();
        }
    }

    /**
     * Bug caught: every release that changes the font set leaves its predecessor's copy (about
     * 30 MB) in the data directory forever.
     */
    @Test
    void aStartRemovesAnOlderCopyAndKeepsTheCurrentOne(@TempDir Path parent) throws IOException {
        Path old = olderCopy(parent);

        TypstAssets assets = new TypstAssets(parent);

        assertThat(old).doesNotExist();
        assertThat(assets.fontDir().getParent().resolve(".installed")).isRegularFile();
        try (Stream<Path> entries = Files.list(parent)) {
            assertThat(entries).containsExactly(assets.fontDir().getParent());
        }
    }

    /**
     * Bug caught: pruning removes everything but the current copy, taking along whatever else
     * lives in the folder: an install another backend is still writing, or a stray file.
     */
    @Test
    void aStartLeavesWhatIsNotAnOlderCopyAlone(@TempDir Path parent) throws IOException {
        Path installing = Files.createDirectories(parent.resolve("installing-123"));
        Path other = Files.createDirectories(parent.resolve("notes"));
        Path file = Files.writeString(parent.resolve("fedcba9876543210"), "not a copy");

        new TypstAssets(parent);

        assertThat(installing).isDirectory();
        assertThat(other).isDirectory();
        assertThat(file).isRegularFile();
    }

    /**
     * Bug caught: on Windows a font still open in an older copy makes the delete throw, so the
     * app fails to start; or the copy is half deleted while still marked installed.
     */
    @Test
    void anOlderCopyWithAnOpenFontIsLeftWholeOrRemoved(@TempDir Path parent) throws IOException {
        Path old = olderCopy(parent);

        // java.io opens without delete sharing, so on Windows the font cannot be deleted or its
        // folder renamed while it is open, as with a font Typst has mapped into memory.
        try (InputStream open = new FileInputStream(old.resolve("fonts/Old.ttf").toFile())) {
            new TypstAssets(parent);
        }

        // Elsewhere an open file does not block the delete, so the copy may go; it must not be
        // left half deleted while still marked installed.
        if (IS_WINDOWS || Files.exists(old)) {
            assertThat(old.resolve(".installed")).isRegularFile();
            assertThat(old.resolve("fonts/Old.ttf")).isRegularFile();
        }
    }

    /**
     * Bug caught: pruning runs only after a successful install, so a disk filled by old copies
     * makes the install fail on every start and the copies that would free the space stay.
     */
    @Test
    void anInstallThatFailsStillRemovesOlderCopies(@TempDir Path root) throws Exception {
        Path current = new TypstAssets(root.resolve("probe")).fontDir().getParent().getFileName();
        Path parent = Files.createDirectories(root.resolve("typst-assets"));
        Path old = olderCopy(parent);
        // A dangling link where the current copy belongs makes moving the new copy into place fail.
        link(parent.resolve(current), root.resolve("missing"));

        assertThatThrownBy(() -> new TypstAssets(parent)).isInstanceOf(IOException.class);

        assertThat(old).doesNotExist();
    }

    /**
     * Bug caught: removing an older copy follows a Windows junction inside it and deletes files
     * outside the asset folder. Only Windows exercises the protection: elsewhere the link is a
     * symlink, which Files.walk does not follow anyway.
     */
    @Test
    void removingAnOlderCopyNeverDeletesThroughALink(@TempDir Path root) throws Exception {
        Path parent = Files.createDirectories(root.resolve("typst-assets"));
        Path outside = Files.createDirectories(root.resolve("documents"));
        Path precious = Files.writeString(outside.resolve("cv.docx"), "hers");
        Path old = olderCopy(parent);
        link(old.resolve("fonts/elsewhere"), outside);

        new TypstAssets(parent);

        assertThat(precious).isRegularFile();
        assertThat(old).doesNotExist();
    }

    private static Path olderCopy(Path parent) throws IOException {
        Path old = Files.createDirectories(parent.resolve(OLD_COPY).resolve("fonts"));
        Files.writeString(old.resolve("Old.ttf"), "font bytes");
        Files.createFile(old.getParent().resolve(".installed"));
        return old.getParent();
    }

    /** A junction on Windows, which needs no admin rights and is what Files.walk descends into. */
    private static void link(Path link, Path target) throws IOException, InterruptedException {
        if (!IS_WINDOWS) {
            Files.createSymbolicLink(link, target);
            return;
        }
        Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true)
                .start();
        assertThat(mklink.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(mklink.exitValue()).isZero();
    }
}
