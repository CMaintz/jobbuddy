package com.autoapplicant.adapter.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
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

    public byte[] renderStructured(StructuredDocument document) {
        String html = structuredHtml(document);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Structured PDF rendering failed", ex);
        }
    }

    private String structuredHtml(StructuredDocument document) {
        boolean cv = document.documentType() != null && "CV".equals(document.documentType().name());
        boolean designed = "DESIGNED".equalsIgnoreCase(document.exportMode())
                || (document.templateId() != null && document.templateId().contains("modern"));
        String css = designed ? designedCss(cv) : atsCss();
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html><html><head><meta charset="UTF-8"><style>
                """);
        html.append(css).append("</style></head><body><article class=\"page ");
        html.append(designed ? "designed" : "ats").append("\">");
        appendIdentity(html, document.identity(), designed);
        if (cv) {
            appendSections(html, document.sections());
        } else {
            html.append("<main class=\"letter-body\">").append(paragraphs(document.bodyContent())).append("</main>");
        }
        html.append("</article></body></html>");
        return html.toString();
    }

    private void appendIdentity(StringBuilder html, DocumentIdentity identity, boolean designed) {
        if (identity == null) return;
        html.append("<header class=\"topbar\">");
        if (designed) {
            html.append("<div class=\"avatar\">").append(initials(identity.name())).append("</div>");
        }
        html.append("<div class=\"person\"><h1>").append(escape(identity.name())).append("</h1>");
        if (identity.headline() != null && !identity.headline().isBlank()) {
            html.append("<p>").append(escape(identity.headline())).append("</p>");
        }
        html.append("</div><div class=\"contact\">");
        appendContact(html, identity.email());
        appendContact(html, identity.phone());
        appendContact(html, identity.location());
        appendContact(html, identity.linkedinUrl());
        appendContact(html, identity.githubUrl());
        appendContact(html, identity.websiteUrl());
        html.append("</div></header>");
    }

    private void appendSections(StringBuilder html, List<StructuredDocumentSection> sections) {
        html.append("<main>");
        for (StructuredDocumentSection section : sections != null ? sections : List.<StructuredDocumentSection>of()) {
            html.append("<section class=\"section section-").append(escape(section.type())).append("\">");
            html.append("<h2>").append(escape(section.heading())).append("</h2>");
            if (section.body() != null && !section.body().isBlank()) {
                html.append("<p class=\"section-body\">").append(escape(section.body())).append("</p>");
            }
            if (section.items() != null && !section.items().isEmpty()) {
                if ("skills".equals(section.type())) {
                    html.append("<ul class=\"skills\">");
                    for (StructuredDocumentItem item : section.items()) {
                        html.append("<li>").append(escape(item.title())).append("</li>");
                    }
                    html.append("</ul>");
                } else {
                    for (StructuredDocumentItem item : section.items()) {
                        appendItem(html, item);
                    }
                }
            }
            html.append("</section>");
        }
        html.append("</main>");
    }

    private void appendItem(StringBuilder html, StructuredDocumentItem item) {
        html.append("<div class=\"item\"><div class=\"item-head\"><div><h3>")
                .append(escape(item.title())).append("</h3>");
        if (item.subtitle() != null && !item.subtitle().isBlank()) {
            html.append("<p class=\"subtitle\">").append(escape(item.subtitle())).append("</p>");
        }
        html.append("</div>");
        if (item.dateRange() != null && !item.dateRange().isBlank()) {
            html.append("<p class=\"dates\">").append(escape(item.dateRange())).append("</p>");
        }
        html.append("</div>");
        if (item.description() != null && !item.description().isBlank()) {
            html.append("<p>").append(escape(item.description())).append("</p>");
        }
        if (item.bullets() != null && !item.bullets().isEmpty()) {
            html.append("<ul>");
            for (String bullet : item.bullets()) html.append("<li>").append(escape(bullet)).append("</li>");
            html.append("</ul>");
        }
        if (item.technologies() != null && !item.technologies().isEmpty()) {
            html.append("<p class=\"tech\">").append(escape(String.join(" · ", item.technologies()))).append("</p>");
        }
        html.append("</div>");
    }

    private void appendContact(StringBuilder html, String value) {
        if (value != null && !value.isBlank()) {
            html.append("<span>").append(escape(value)).append("</span>");
        }
    }

    private String paragraphs(String content) {
        if (content == null || content.isBlank()) return "";
        String[] parts = content.split("\\R\\s*\\R|\\R");
        StringBuilder html = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) html.append("<p>").append(escape(part.trim())).append("</p>");
        }
        return html.toString();
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return "";
        StringBuilder initials = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isBlank()) initials.append(Character.toUpperCase(part.charAt(0)));
            if (initials.length() == 2) break;
        }
        return escape(initials.toString());
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String atsCss() {
        return """
                body{font-family:Arial,sans-serif;font-size:10.5pt;color:#111;margin:0}.page{width:210mm;min-height:297mm;padding:18mm;box-sizing:border-box}.topbar{border-bottom:1px solid #222;padding-bottom:8px;margin-bottom:14px}.person h1{font-size:20pt;margin:0 0 3px}.person p{margin:0 0 6px}.contact{font-size:9pt;line-height:1.5}.contact span:after{content:" | "}.contact span:last-child:after{content:""}h2{font-size:11pt;text-transform:uppercase;letter-spacing:0;margin:14px 0 6px;border-bottom:1px solid #ddd}.section{break-inside:avoid}.section-body{line-height:1.5}.item{break-inside:avoid;margin:0 0 10px}.item-head{display:flex;justify-content:space-between;gap:12px}.item h3{font-size:10.5pt;margin:0}.subtitle,.dates{margin:0;color:#333}.dates{white-space:nowrap}.item p{margin:3px 0}.item ul{margin:4px 0 0 18px;padding:0}.skills{display:block;columns:2;margin:0;padding-left:18px}.tech{font-size:9pt;color:#333}
                """;
    }

    private String designedCss(boolean cv) {
        return """
                body{font-family:Arial,sans-serif;font-size:10.5pt;color:#172033;margin:0;background:#fff}.page{width:210mm;min-height:297mm;padding:0;box-sizing:border-box}.topbar{display:flex;align-items:center;gap:14px;background:#18324a;color:white;padding:16mm 18mm 12mm}.avatar{width:20mm;height:20mm;border-radius:50%;background:#eef6ff;color:#18324a;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:16pt;flex:0 0 auto}.person{flex:1}.person h1{font-size:24pt;margin:0 0 3px}.person p{margin:0;color:#cce0f0}.contact{display:flex;flex-direction:column;gap:2px;text-align:right;font-size:8.5pt}.contact span{color:#eef6ff}main,.letter-body{padding:14mm 18mm 18mm}.letter-body p{line-height:1.7;margin:0 0 10px}.section{break-inside:avoid;margin-bottom:12px}.section h2{font-size:11pt;text-transform:uppercase;letter-spacing:0;color:#18324a;border-bottom:1px solid #d7e1ea;padding-bottom:3px;margin:0 0 8px}.section-body{font-size:10.5pt;line-height:1.55;margin:0}.item{break-inside:avoid;margin:0 0 10px}.item-head{display:flex;justify-content:space-between;gap:12px}.item h3{font-size:11pt;margin:0;color:#101827}.subtitle,.dates{margin:1px 0 0;color:#526071}.dates{white-space:nowrap;font-size:9pt}.item p{margin:4px 0}.item ul{margin:5px 0 0 18px;padding:0}.item li{margin-bottom:2px}.skills{display:flex;flex-wrap:wrap;gap:6px;list-style:none;margin:0;padding:0}.skills li{border:1px solid #cbd8e3;background:#f7fafc;border-radius:4px;padding:3px 7px}.tech{font-size:9pt;color:#526071}
                """;
    }
}
