package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.structured.StructuredDocument;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Runs the letter gate again on a saved letter, for a user who edited it after generation. */
public interface RecheckLetterUseCase {

    /**
     * Judges the letter's current text once, without rewriting it, and stores that verdict on the
     * row in place of the old one.
     *
     * @return the stored letter with its new review; completes with {@link IllegalArgumentException}
     *     when the document is not the user's, and with {@link IllegalStateException} when it is not
     *     a letter written to a posting, which leaves the gate nothing to judge it against
     */
    CompletableFuture<StructuredDocument> recheck(UUID userId, UUID generatedDocumentId);
}
