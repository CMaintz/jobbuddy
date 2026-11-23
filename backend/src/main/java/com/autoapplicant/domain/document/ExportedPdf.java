package com.autoapplicant.domain.document;

import java.util.Optional;

/**
 * An exported PDF and how it sits against its page budget. The budget is empty when the page
 * count could not be read, so a caller never reports a document as fitting when it does not know.
 */
public record ExportedPdf(byte[] bytes, Optional<PageBudget> pageBudget) {}
