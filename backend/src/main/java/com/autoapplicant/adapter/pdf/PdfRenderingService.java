package com.autoapplicant.adapter.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        DocumentTheme theme = resolveTheme(document.options() != null ? document.options().theme() : null);
        String css = designed ? designedCss(cv, theme) : atsCss(theme);
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html><html><head><meta charset="UTF-8"><style>
                """);
        html.append(css).append("</style></head><body><article class=\"page ");
        html.append(designed ? "designed" : "ats").append("\">");
        appendIdentity(html, document.identity(), designed,
                document.options() != null && document.options().showProfileImage());
        if (cv) {
            appendSections(html, document.sections());
        } else {
            html.append("<main class=\"letter-body\">").append(paragraphs(document.bodyContent())).append("</main>");
        }
        html.append("</article></body></html>");
        return html.toString();
    }

    private void appendIdentity(StringBuilder html, DocumentIdentity identity, boolean designed, boolean showProfileImage) {
        if (identity == null) return;
        html.append("<header class=\"topbar\">");
        if (designed && showProfileImage) {
            html.append("<div class=\"avatar\">");
            if (identity.profileImageUrl() != null && !identity.profileImageUrl().isBlank()) {
                html.append("<img src=\"").append(escape(identity.profileImageUrl())).append("\" alt=\"\"/>");
            } else {
                html.append(initials(identity.name()));
            }
            html.append("</div>");
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

    private String atsCss(DocumentTheme theme) {
        String template = loadCssTemplate("cv-ats.css");
        return template
                .replace("{{FONT}}", cssFont(theme.fontFamily()))
                .replace("{{BASE_PT}}", baseFontPt(theme.fontScale()))
                .replace("{{H1_PT}}", h1FontPt(theme.fontScale()));
    }

    private String designedCss(boolean cv, DocumentTheme theme) {
        String template = loadCssTemplate("cv-designed.css");
        return template
                .replace("{{FONT}}", cssFont(theme.fontFamily()))
                .replace("{{BASE_PT}}", baseFontPt(theme.fontScale()))
                .replace("{{H1_PT}}", h1FontPt(theme.fontScale()))
                .replace("{{PRIMARY}}", cssColor(theme.primaryColor()))
                .replace("{{ACCENT}}", cssColor(theme.accentColor()));
    }

    private String loadCssTemplate(String filename) {
        try (InputStream is = getClass().getResourceAsStream("/pdf/" + filename)) {
            if (is == null) throw new IllegalStateException("CSS template not found: " + filename);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load CSS template: " + filename, e);
        }
    }

    private DocumentTheme resolveTheme(DocumentTheme theme) {
        DocumentTheme defaults = DocumentTheme.defaults();
        if (theme == null) return defaults;
        return new DocumentTheme(
                isHexColor(theme.primaryColor()) ? theme.primaryColor() : defaults.primaryColor(),
                isHexColor(theme.accentColor()) ? theme.accentColor() : defaults.accentColor(),
                safeFont(theme.fontFamily()),
                safeFontScale(theme.fontScale()));
    }

    private boolean isHexColor(String value) {
        return value != null && value.matches("#[0-9a-fA-F]{6}");
    }

    private String cssColor(String value) {
        return isHexColor(value) ? value : "#18324a";
    }

    private String safeFont(String value) {
        if (value == null) return "Arial";
        return switch (value) {
            case "Arial", "Inter", "Georgia", "Calibri", "Times New Roman" -> value;
            default -> "Arial";
        };
    }

    private String cssFont(String value) {
        String safe = safeFont(value);
        return safe.contains(" ") ? "'" + safe + "',serif" : safe + ",sans-serif";
    }

    private String safeFontScale(String value) {
        if (value == null) return "normal";
        return switch (value) {
            case "small", "normal", "large" -> value;
            default -> "normal";
        };
    }

    private String baseFontPt(String scale) {
        return switch (safeFontScale(scale)) {
            case "small" -> "9.8";
            case "large" -> "11.2";
            default -> "10.5";
        };
    }

    private String h1FontPt(String scale) {
        return switch (safeFontScale(scale)) {
            case "small" -> "21";
            case "large" -> "27";
            default -> "24";
        };
    }
}
