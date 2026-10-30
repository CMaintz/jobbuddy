package com.autoapplicant.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The SSRF guard must still let a document embed the app's own uploads, such as a profile photo. */
class PdfRenderingServiceUploadsTest {

    @TempDir Path uploads;

    @Test
    void profileImageFromUploadsIsEmbedded() throws IOException {
        PdfTestImages.writePng(uploads.resolve("logo.png"));
        PdfRenderingService service = new PdfRenderingService(new PdfResourcePolicy(uploads));

        byte[] pdf = service.renderStructured(PdfTestDocuments.withProfileImage("/uploads/logo.png"));

        assertThat(PdfTestImages.imagesOnFirstPage(pdf)).isPositive();
    }
}
