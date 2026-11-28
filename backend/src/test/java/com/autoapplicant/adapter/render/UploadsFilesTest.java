package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The only local files a render may read are the app's own uploads. Each case is a way a
 * document reference could reach another file: remote hosts, traversals, file: URIs elsewhere,
 * symlinks out of uploads and UNC shares (which on Windows would leak NTLM credentials).
 */
class UploadsFilesTest {

    private static final String PHOTO = "/uploads/profile-photos/abc.jpg";
    private static final byte[] PHOTO_BYTES = "jpeg".getBytes(StandardCharsets.UTF_8);

    @TempDir Path uploads;
    @TempDir Path outside;

    @Test
    void rejectsRemoteReferences() {
        UploadsFiles files = new UploadsFiles(uploads);

        assertThat(files.resolve("http://169.254.169.254/latest/meta-data/")).isEmpty();
        assertThat(files.resolve("https://example.com/logo.png")).isEmpty();
        assertThat(files.resolve("//internal-host/x.png")).isEmpty();
    }

    @Test
    void rejectsUploadsPathsThatEscapeTheUploadsDirectory() throws IOException {
        UploadsFiles files = new UploadsFiles(uploads);
        Files.writeString(uploads.resolveSibling("secrets.txt"), "secret");

        assertThat(files.resolve("/uploads/../secrets.txt")).isEmpty();
        assertThat(files.resolve("/uploads/profile-photos/../../etc/passwd")).isEmpty();
        assertThat(files.resolve("/uploads/")).isEmpty();
    }

    @Test
    void resolvesOwnUploadsFromLocalStorage() throws IOException {
        Path photo = writePhoto();

        assertThat(new UploadsFiles(uploads).resolve(PHOTO)).contains(photo.toRealPath());
    }

    @Test
    void acceptsAFileUriInsideUploads() throws IOException {
        Path photo = writePhoto();

        assertThat(new UploadsFiles(uploads).resolve(photo.toUri().toString())).contains(photo.toRealPath());
    }

    @Test
    void rejectsFileUrisOutsideUploads() throws IOException {
        UploadsFiles files = new UploadsFiles(uploads);
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");

        assertThat(files.resolve(secret.toUri().toString())).isEmpty();
        String traversal = uploads.relativize(secret).toString().replace('\\', '/');
        assertThat(traversal).startsWith("..");
        assertThat(files.resolve(uploads.toUri() + traversal)).isEmpty();
    }

    @Test
    void rejectsASymlinkInsideUploadsThatPointsOutside() throws IOException {
        UploadsFiles files = new UploadsFiles(uploads);
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");
        assumeTrue(symlink(uploads.resolve("link.txt"), secret), "symlinks unavailable here");

        assertThat(files.resolve("/uploads/link.txt")).isEmpty();
        assertThat(files.resolve(uploads.resolve("link.txt").toUri().toString())).isEmpty();
    }

    @Test
    void rejectsUncSharesAndHostsWithoutTouchingThem() {
        UploadsFiles files = new UploadsFiles(uploads);

        assertThat(files.resolve("/uploads/\\\\unc-probe.invalid\\share\\x.png")).isEmpty();
        assertThat(files.resolve("/uploads///unc-probe.invalid/share/x.png")).isEmpty();
        assertThat(files.resolve("/uploads/C:/Windows/win.ini")).isEmpty();
        assertThat(files.resolve("file://unc-probe.invalid/share/x.png")).isEmpty();
        assertThat(files.resolve("file:////unc-probe.invalid/share/x.png")).isEmpty();
    }

    private Path writePhoto() throws IOException {
        Path photo = uploads.resolve("profile-photos/abc.jpg");
        Files.createDirectories(photo.getParent());
        return Files.write(photo, PHOTO_BYTES);
    }

    private static boolean symlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            return false;
        }
    }
}
