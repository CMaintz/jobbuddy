package com.autoapplicant.adapter.web.dto;

import com.autoapplicant.domain.document.structured.DocumentTheme;

public record DocumentThemeRequest(
        String primaryColor,
        String accentColor,
        String fontFamily,
        String fontScale
) {
    public DocumentTheme toTheme() {
        return new DocumentTheme(primaryColor, accentColor, fontFamily, fontScale);
    }
}
