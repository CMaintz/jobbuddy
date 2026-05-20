package com.autoapplicant.domain.document.structured;

public record DocumentTheme(
        String primaryColor,
        String accentColor,
        String fontFamily,
        String fontScale
) {
    public static DocumentTheme defaults() {
        return new DocumentTheme("#18324a", "#cbd8e3", "Arial", "normal");
    }
}
