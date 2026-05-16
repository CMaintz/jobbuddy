package com.autoapplicant.adapter.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Service
public class PdfRenderingService {

    public byte[] render(String htmlTemplate, String cssStyles, Map<String, String> placeholders) {
        String html = htmlTemplate.replace("{{CSS}}", cssStyles != null ? cssStyles : "");

        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            html = html.replace("{{" + e.getKey() + "}}", e.getValue() != null ? e.getValue() : "");
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("PDF rendering failed", ex);
        }
    }
}
