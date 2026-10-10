package com.autoapplicant.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** User-authored templates must not make the renderer fetch remote or local-file resources. */
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
    void templateReferencesToInternalHostsAndFilesAreNeverFetched() {
        byte[] pdf = new PdfRenderingService().render(hostileTemplate(), "", Map.of());

        assertThat(pdf).isNotEmpty();
        assertThat(requests.get()).isZero();
    }

    @Test
    void sanityCheckTheDefaultRendererWouldHaveFetched() throws Exception {
        PdfRendererBuilder unguarded = new PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(templateWith("").replace("{{CSS}}", ""), null);
        unguarded.toStream(new ByteArrayOutputStream()).run();

        assertThat(requests.get()).isPositive();
    }

    @Test
    void escapeCoversQuotesSoValuesCannotBreakOutOfAttributes() {
        String escaped = new PdfRenderingService().escape("x\" onerror=\"y' <b>&");

        assertThat(escaped).isEqualTo("x&quot; onerror=&quot;y&#39; &lt;b&gt;&amp;");
    }

    private String hostileTemplate() {
        return templateWith("<img src=\"http://169.254.169.254/latest/meta-data/\"/>"
                + "<img src=\"file:///etc/passwd\"/>");
    }

    /** Only the loopback server is referenced here, so the unguarded sanity render cannot hang. */
    private String templateWith(String extraMarkup) {
        String internal = "http://127.0.0.1:" + server.getAddress().getPort();
        return "<html><head><style>{{CSS}} body { background: url('" + internal + "/bg'); }</style>"
                + "<link rel=\"stylesheet\" href=\"" + internal + "/style.css\"/></head><body>"
                + "<img src=\"" + internal + "/latest/meta-data\"/>"
                + extraMarkup
                + "<p>Hello</p></body></html>";
    }
}
