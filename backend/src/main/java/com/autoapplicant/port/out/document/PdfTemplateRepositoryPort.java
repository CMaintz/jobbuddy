package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.PdfTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PdfTemplateRepositoryPort {
    List<PdfTemplate> findAvailable(UUID userId);
    Optional<PdfTemplate> findById(UUID id);
    PdfTemplate save(PdfTemplate template);
    void deleteById(UUID id);
}
