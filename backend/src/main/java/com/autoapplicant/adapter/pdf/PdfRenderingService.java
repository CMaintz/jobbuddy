package com.autoapplicant.adapter.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class PdfRenderingService {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderingService.class);

    /** When true, a CV over 2 pages (or a letter over 1) fails rendering instead of just warning. */
    @Value("${app.pdf.page-budget.strict:false}")
    private boolean pageBudgetStrict;

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
            byte[] pdf = baos.toByteArray();
            enforcePageBudget(pdf, document);
            return pdf;
        } catch (Exception ex) {
            throw new RuntimeException("Structured PDF rendering failed", ex);
        }
    }

    /**
     * Post-render page-budget check: a CV should be 2 pages or fewer, other documents 1.
     * Measures the true page count of the rendered PDF (layout is never mutated to fit).
     * Warns by default; fails when {@code app.pdf.page-budget.strict=true}.
     */
    private void enforcePageBudget(byte[] pdf, StructuredDocument document) {
        boolean cv = document.documentType() != null && "CV".equals(document.documentType().name());
        int max = cv ? 2 : 1;
        try (PDDocument doc = PDDocument.load(pdf)) {
            int pages = doc.getNumberOfPages();
            if (pages > max) {
                String msg = "PDF page budget exceeded: " + (cv ? "CV" : "document")
                        + " rendered " + pages + " pages (max " + max + ")";
                if (pageBudgetStrict) throw new IllegalStateException(msg);
                log.warn(msg);
            }
        } catch (IOException e) {
            log.warn("Could not verify PDF page count: {}", e.getMessage());
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

    // Contact icons (inline SVG, 12 px, compatible with openhtmltopdf)
    private static final String ICON_EMAIL =
        "<svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"#7a8a99\" style=\"display:inline-block;vertical-align:middle;margin-right:4px\">" +
        "<path d=\"M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z\"/></svg>";
    private static final String ICON_PHONE =
        "<svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"#7a8a99\" style=\"display:inline-block;vertical-align:middle;margin-right:4px\">" +
        "<path d=\"M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z\"/></svg>";
    private static final String ICON_LOCATION =
        "<svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"#7a8a99\" style=\"display:inline-block;vertical-align:middle;margin-right:4px\">" +
        "<path d=\"M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z\"/></svg>";
    private static final String ICON_LINKEDIN =
        "<svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"#7a8a99\" style=\"display:inline-block;vertical-align:middle;margin-right:4px\">" +
        "<path d=\"M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 01-2.063-2.065 2.064 2.064 0 112.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z\"/></svg>";
    private static final String ICON_GITHUB =
        "<svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"#7a8a99\" style=\"display:inline-block;vertical-align:middle;margin-right:4px\">" +
        "<path d=\"M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12\"/></svg>";
    private static final String ICON_GLOBE =
        "<svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"#7a8a99\" style=\"display:inline-block;vertical-align:middle;margin-right:4px\">" +
        "<path d=\"M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z\"/></svg>";

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
        appendContactWithIcon(html, identity.email(), ICON_EMAIL);
        appendContactWithIcon(html, identity.phone(), ICON_PHONE);
        appendContactWithIcon(html, identity.location(), ICON_LOCATION);
        appendContactWithIcon(html, identity.linkedinUrl(), ICON_LINKEDIN);
        appendContactWithIcon(html, identity.githubUrl(), ICON_GITHUB);
        appendContactWithIcon(html, identity.websiteUrl(), ICON_GLOBE);
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
                if ("skills".equals(section.type()) || "custom".equals(section.type())) {
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

    private void appendContactWithIcon(StringBuilder html, String value, String iconSvg) {
        if (value != null && !value.isBlank()) {
            html.append("<span>").append(iconSvg).append(escape(value)).append("</span>");
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
        return normalizeAtsText(value)
                .replaceAll("[\\u00A0\\u2009\\u202F]", " ")        // nbsp / thin / narrow -> space
                .replaceAll("[\\u200B\\u200C\\u200D\\uFEFF]", "")  // zero-width chars -> removed
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Normalizes ATS-hostile Unicode in the PDF text layer to plain ASCII equivalents.
     * Parsers read the embedded text, not the rendered glyphs; smart quotes, en/em dashes,
     * ellipses, non-breaking/thin spaces, and zero-width characters all degrade keyword
     * extraction, and models routinely emit them. The decorative middot separator (U+00B7)
     * inserted by this renderer is intentionally left alone.
     */
    private static String normalizeAtsText(String value) {
        return value
                .replace('‘', '\'').replace('’', '\'')  // smart single quotes
                .replace('“', '"').replace('”', '"')    // smart double quotes
                .replace('–', '-').replace('—', '-')    // en / em dash
                .replace('−', '-')                           // minus sign
                .replace("…", "...")                         // ellipsis
                .replace(' ', ' ').replace(' ', ' ').replace(' ', ' ') // nbsp / narrow / thin
                .replace("​", "").replace("‌", "")      // zero-width space / non-joiner
                .replace("‍", "").replace("﻿", "");     // zero-width joiner / BOM
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
