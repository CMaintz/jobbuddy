package com.autoapplicant.adapter.pdf;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.DocumentRenderOptions;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import java.util.List;

/**
 * A designed letter showing its profile image: the one place a client-supplied URL reaches the
 * renderer, since /export-structured-pdf takes the whole document from the request body.
 */
final class PdfTestDocuments {

    private PdfTestDocuments() {}

    static StructuredDocument withProfileImage(String imageUrl) {
        DocumentIdentity identity =
                new DocumentIdentity("Ada Lovelace", null, null, null, null, null, null, null, imageUrl);
        DocumentRenderOptions options = new DocumentRenderOptions(true, DocumentTheme.defaults());
        return new StructuredDocument(null, DocumentType.COVER_LETTER, "DESIGNED", null, identity, options,
                List.of(), "Hello", null);
    }
}
