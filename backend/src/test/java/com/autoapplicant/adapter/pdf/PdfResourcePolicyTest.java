package com.autoapplicant.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceType;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfResourcePolicyTest {

    @TempDir Path uploads;

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
    void rejectsUploadsPathsThatEscapeTheUploadsDirectory() {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);

        assertThat(policy.resolveURI(null, "/uploads/../secrets.txt")).isNull();
        assertThat(policy.resolveURI(null, "/uploads/profile-photos/../../etc/passwd")).isNull();
        assertThat(policy.resolveURI(null, "/uploads/")).isNull();
    }

    @Test
    void resolvesOwnUploadsFromLocalStorage() {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);

        String resolved = policy.resolveURI(null, "/uploads/profile-photos/abc.jpg");

        assertThat(resolved).isEqualTo(uploads.resolve("profile-photos/abc.jpg").toUri().toString());
        assertThat(policy.test(resolved, ExternalResourceType.IMAGE_RASTER)).isTrue();
    }

    @Test
    void passesDataUrisThrough() {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);
        String dataUri = "data:image/png;base64,iVBORw0KGgo=";

        assertThat(policy.resolveURI(null, dataUri)).isEqualTo(dataUri);
        assertThat(policy.test(dataUri, ExternalResourceType.IMAGE_RASTER)).isTrue();
    }

    @Test
    void accessControlRejectsAnythingOutsideUploads() {
        PdfResourcePolicy policy = new PdfResourcePolicy(uploads);

        assertThat(policy.test("http://169.254.169.254/latest", ExternalResourceType.IMAGE_RASTER)).isFalse();
        assertThat(policy.test("file:///etc/passwd", ExternalResourceType.CSS)).isFalse();
        assertThat(policy.test(null, ExternalResourceType.CSS)).isFalse();
    }
}
