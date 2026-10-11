package com.autoapplicant.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfResourcePolicyTest {

    private static final String PHOTO = "/uploads/profile-photos/abc.jpg";
    private static final byte[] PHOTO_BYTES = "jpeg".getBytes(StandardCharsets.UTF_8);

    @TempDir Path uploads;
    @TempDir Path outside;

    @Test
    void rejectsRemoteAndLocalFileReferences() {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);

        assertThat(policy.resolveURI(null, "http://169.254.169.254/latest/meta-data/")).isNull();
        assertThat(policy.resolveURI(null, "https://example.com/logo.png")).isNull();
        assertThat(policy.resolveURI(null, "file:///etc/passwd")).isNull();
        assertThat(policy.resolveURI(null, "//internal-host/x.png")).isNull();
        assertThat(policy.resolveURI("http://10.0.0.1/", "relative.png")).isNull();
    }

    @Test
    void rejectsUploadsPathsThatEscapeTheUploadsDirectory() throws IOException {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);
        Files.writeString(uploads.resolveSibling("secrets.txt"), "secret");

        assertThat(policy.resolveURI(null, "/uploads/../secrets.txt")).isNull();
        assertThat(policy.resolveURI(null, "/uploads/profile-photos/../../etc/passwd")).isNull();
        assertThat(policy.resolveURI(null, "/uploads/")).isNull();
    }

    @Test
    void resolvesOwnUploadsFromLocalStorage() throws IOException {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);
        Path photo = writePhoto();

        assertThat(policy.resolveURI(null, PHOTO)).isEqualTo(photo.toRealPath().toUri().toString());
    }

    @Test
    void resolvingAnAlreadyResolvedUploadsUriIsStable() throws IOException {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);
        writePhoto();

        String resolved = policy.resolveURI(null, PHOTO);

        assertThat(resolved).isNotNull();
        assertThat(policy.resolveURI(null, resolved)).isEqualTo(resolved);
    }

    @Test
    void rejectsFileUrisOutsideUploads() throws IOException {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");

        assertThat(policy.resolveURI(null, secret.toUri().toString())).isNull();
        String traversal = uploads.relativize(secret).toString().replace('\\', '/');
        assertThat(traversal).startsWith("..");
        assertThat(policy.resolveURI(null, uploads.toUri() + traversal)).isNull();
    }

    @Test
    void rejectsASymlinkInsideUploadsThatPointsOutside() throws IOException {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");
        assumeTrue(symlink(uploads.resolve("link.txt"), secret), "symlinks unavailable here");

        assertThat(policy.resolveURI(null, "/uploads/link.txt")).isNull();
        assertThat(policy.resolveURI(null, uploads.resolve("link.txt").toUri().toString())).isNull();
    }

    @Test
    void rejectsUncSharesAndHostsWithoutTouchingThem() {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);

        assertThat(policy.resolveURI(null, "/uploads/\\\\unc-probe.invalid\\share\\x.png")).isNull();
        assertThat(policy.resolveURI(null, "/uploads///unc-probe.invalid/share/x.png")).isNull();
        assertThat(policy.resolveURI(null, "/uploads/C:/Windows/win.ini")).isNull();
        assertThat(policy.resolveURI(null, "file://unc-probe.invalid/share/x.png")).isNull();
        assertThat(policy.resolveURI(null, "file:////unc-probe.invalid/share/x.png")).isNull();
        assertThat(policy.getUrl("file://unc-probe.invalid/share/x.png").getStream()).isNull();
    }

    @Test
    void passesDataUrisThrough() {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);
        String dataUri = "data:image/png;base64,iVBORw0KGgo=";

        assertThat(policy.resolveURI(null, dataUri)).isEqualTo(dataUri);
    }

    @Test
    void streamFactoryOpensUploadsFilesOnly() throws IOException {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);
        Path photo = writePhoto();
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");

        try (InputStream stream = policy.getUrl(photo.toUri().toString()).getStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(PHOTO_BYTES);
        }
        assertThat(policy.getUrl(secret.toUri().toString()).getStream()).isNull();
        assertThat(policy.getUrl(secret.toUri().toString()).getReader()).isNull();
        assertThat(policy.getUrl("http://169.254.169.254/latest").getStream()).isNull();
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
