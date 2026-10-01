package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.PdfTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagePdfTemplatesUseCase {
    List<PdfTemplate> getTemplates(UUID userId);
    /** A system template or one of the caller's own; anyone else's reads as absent. */
    Optional<PdfTemplate> getById(UUID id, UUID userId);
    PdfTemplate create(PdfTemplate template);
    PdfTemplate update(PdfTemplate template);
    void delete(UUID id, UUID userId);
}
