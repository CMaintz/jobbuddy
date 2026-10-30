package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.pdf.PdfTestImages;
import com.autoapplicant.port.out.document.DocumentRendererPort.RenderedPdf;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Document images follow the SSRF rule: an inline data: image or a file from the app's own
 * uploads is embedded, anything else (internal hosts, local files elsewhere) is dropped and never
 * fetched.
 */
class TypstImageResourcesTest {

    private static final int NOT_FOUND = 404;

    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;

    @TempDir Path uploads;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(NOT_FOUND, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Bug caught: an image URL pointing at an internal host is fetched by the server. */
    @Test
    void imageOnAnInternalHostIsNeverFetched() throws IOException {
        String internal = "http://127.0.0.1:" + server.getAddress().getPort() + "/latest/meta-data/photo.png";

        RenderedPdf pdf = render(internal);

        assertThat(requests.get()).isZero();
        assertThat(PdfTestImages.imagesOnFirstPage(pdf.bytes())).isZero();
    }

    /** Bug caught: a file: URI outside uploads is copied in, leaking any server file the app can read. */
    @Test
    void localFileOutsideUploadsIsNotEmbedded(@TempDir Path elsewhere) throws IOException {
        String secret = PdfTestImages.writePng(elsewhere.resolve("secret.png")).toUri().toString();

        assertThat(PdfTestImages.imagesOnFirstPage(render(secret).bytes())).isZero();
    }

    /** Bug caught: the guard is so strict that the user's own uploaded photo disappears. */
    @Test
    void photoFromUploadsIsEmbedded() throws IOException {
        PdfTestImages.writePng(uploads.resolve("photo.png"));

        assertThat(PdfTestImages.imagesOnFirstPage(render("/uploads/photo.png").bytes())).isPositive();
    }

    /** Bug caught: inline data: images (the photo picker's format) are dropped. */
    @Test
    void inlineDataImageIsEmbedded(@TempDir Path scratch) throws IOException {
        byte[] png = Files.readAllBytes(PdfTestImages.writePng(scratch.resolve("inline.png")));
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);

        assertThat(PdfTestImages.imagesOnFirstPage(render(dataUri).bytes())).isPositive();
    }

    /**
     * Bug caught: the stager trusts the file extension, so an upload stored as .jpg that holds
     * another format (an iPhone HEIC photo) makes Typst fail the whole export instead of
     * leaving the photo out.
     */
    @Test
    void undecodablePhotoIsLeftOutInsteadOfFailingTheExport() throws IOException {
        Files.write(uploads.resolve("photo.jpg"), "ftypheic not really a jpeg".getBytes(StandardCharsets.US_ASCII));

        assertThat(PdfTestImages.imagesOnFirstPage(render("/uploads/photo.jpg").bytes())).isZero();
    }

    private RenderedPdf render(String photoUrl) {
        return TypstFixtures.adapter(uploads).renderPdf(TypstFixtures.designedCvWithPhoto(photoUrl));
    }
}
