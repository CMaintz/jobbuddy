package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.document.PdfTemplate;
import com.autoapplicant.port.in.document.ManagePdfTemplatesUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pdf-templates")
@Tag(name = "PDF Templates")
public class PdfTemplateController {

    private final ManagePdfTemplatesUseCase service;
    private final SecurityContextHelper secCtx;

    public PdfTemplateController(ManagePdfTemplatesUseCase service, SecurityContextHelper secCtx) {
        this.service = service;
        this.secCtx = secCtx;
    }

    @GetMapping
    public ResponseEntity<List<PdfTemplate>> list() {
        return ResponseEntity.ok(service.getTemplates(secCtx.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PdfTemplate> getById(@PathVariable UUID id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PdfTemplate> create(@RequestBody PdfTemplate template) {
        UUID userId = secCtx.getCurrentUserId();
        PdfTemplate toCreate = new PdfTemplate(null, userId, template.name(), template.description(),
                template.documentType(), template.htmlTemplate(), template.cssStyles(),
                false, true, null);
        return ResponseEntity.ok(service.create(toCreate));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PdfTemplate> update(@PathVariable UUID id, @RequestBody PdfTemplate template) {
        UUID userId = secCtx.getCurrentUserId();
        PdfTemplate toUpdate = new PdfTemplate(id, userId, template.name(), template.description(),
                template.documentType(), template.htmlTemplate(), template.cssStyles(),
                false, template.isActive(), null);
        return ResponseEntity.ok(service.update(toUpdate));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
