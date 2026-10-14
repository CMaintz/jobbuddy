package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.DocumentTheme;

/** Template and theme defaults shared by every structured-document builder. */
final class DocumentTemplates {

    private static final String ATS = "ATS";
    private static final String DESIGNED = "DESIGNED";

    private DocumentTemplates() {}

    static String resolveTemplate(String templateId, String defaultTemplate) {
        return templateId != null && !templateId.isBlank() ? templateId : defaultTemplate;
    }

    /** The template decides the format; export mode is never a separate user input. */
    static String exportModeFromTemplate(String templateId) {
        if (templateId == null) return ATS;
        String lower = templateId.toLowerCase();
        return lower.contains("ats") ? ATS : DESIGNED;
    }

    static DocumentTheme resolveTheme(DocumentTheme theme) {
        DocumentTheme defaults = DocumentTheme.defaults();
        if (theme == null) return defaults;
        return new DocumentTheme(
                CvDocumentAssembler.firstPresent(theme.primaryColor(), defaults.primaryColor()),
                CvDocumentAssembler.firstPresent(theme.accentColor(), defaults.accentColor()),
                CvDocumentAssembler.firstPresent(theme.fontFamily(), defaults.fontFamily()),
                CvDocumentAssembler.firstPresent(theme.fontScale(), defaults.fontScale()));
    }
}
