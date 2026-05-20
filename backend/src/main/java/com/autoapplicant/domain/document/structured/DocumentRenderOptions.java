package com.autoapplicant.domain.document.structured;

public record DocumentRenderOptions(
        boolean showProfileImage,
        DocumentTheme theme
) {
    public static DocumentRenderOptions defaults() {
        return new DocumentRenderOptions(false, DocumentTheme.defaults());
    }
}
