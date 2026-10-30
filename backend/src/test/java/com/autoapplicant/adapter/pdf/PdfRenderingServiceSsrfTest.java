package com.autoapplicant.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A client-supplied document must not make the renderer fetch remote or local-file resources. */
class PdfRenderingServiceSsrfTest {

    private static final int NOT_FOUND = 404;

    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
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

    @Test
    void profileImageOnAnInternalHostIsNeverFetched() {
        String internal = "http://127.0.0.1:" + server.getAddress().getPort() + "/latest/meta-data";

        byte[] pdf = new PdfRenderingService().renderStructured(PdfTestDocuments.withProfileImage(internal));

        assertThat(pdf).isNotEmpty();
        assertThat(requests.get()).isZero();
    }

    @Test
    void profileImageFromALocalFileOutsideUploadsIsNotEmbedded(@TempDir Path uploads, @TempDir Path elsewhere)
            throws Exception {
        String secret = PdfTestImages.writePng(elsewhere.resolve("secret.png")).toUri().toString();
        PdfRenderingService guarded = new PdfRenderingService(new PdfResourcePolicy(uploads));

        byte[] unguardedPdf = renderUnguarded("<html><body><img src=\"" + secret + "\"/></body></html>");
        byte[] guardedPdf = guarded.renderStructured(PdfTestDocuments.withProfileImage(secret));

        assertThat(PdfTestImages.imagesOnFirstPage(unguardedPdf)).isPositive();
        assertThat(PdfTestImages.imagesOnFirstPage(guardedPdf)).isZero();
    }

    @Test
    void sanityCheckTheDefaultRendererWouldHaveFetched() throws Exception {
        PdfRendererBuilder unguarded = new PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(internalReferences(), null);
        unguarded.toStream(new ByteArrayOutputStream()).run();

        assertThat(requests.get()).isPositive();
    }

    @Test
    void streamGateAloneBlocksFetchesTheDefaultResolverAllows() throws Exception {
        PdfResourcePolicy policy = new PdfResourcePolicy();
        PdfRendererBuilder resolverless = new PdfRendererBuilder()
                .useFastMode()
                .useProtocolsStreamImplementation(policy, PdfResourcePolicy.GUARDED_SCHEMES)
                .withHtmlContent(internalReferences(), null);
        resolverless.toStream(new ByteArrayOutputStream()).run();

        assertThat(requests.get()).isZero();
    }

    @Test
    void escapeCoversQuotesSoValuesCannotBreakOutOfAttributes() {
        String escaped = new PdfRenderingService().escape("x\" onerror=\"y' <b>&");

        assertThat(escaped).isEqualTo("x&quot; onerror=&quot;y&#39; &lt;b&gt;&amp;");
    }

    private static byte[] renderUnguarded(String html) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PdfRendererBuilder().useFastMode().withHtmlContent(html, null).toStream(out).run();
        return out.toByteArray();
    }

    /** Only the loopback server is referenced here, so the unguarded sanity render cannot hang. */
    private String internalReferences() {
        String internal = "http://127.0.0.1:" + server.getAddress().getPort();
        return "<html><head><style>body { background: url('" + internal + "/bg'); }</style>"
                + "<link rel=\"stylesheet\" href=\"" + internal + "/style.css\"/></head><body>"
                + "<img src=\"" + internal + "/latest/meta-data\"/>"
                + "<p>Hello</p></body></html>";
    }
}
