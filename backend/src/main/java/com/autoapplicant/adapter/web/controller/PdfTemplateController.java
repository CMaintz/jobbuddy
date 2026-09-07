package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.document.PdfTemplate;
import com.autoapplicant.port.in.document.ManagePdfTemplatesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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

    @Operation(summary = "List PDF templates")
    @GetMapping
    public ResponseEntity<List<PdfTemplate>> list() {
        return ResponseEntity.ok(service.getTemplates(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Get PDF template by id")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Template not found"))
    @GetMapping("/{id}")
    public ResponseEntity<PdfTemplate> getById(@PathVariable UUID id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create PDF template")
    @ApiResponse(responseCode = "201", description = "Template created")
    @PostMapping
    public ResponseEntity<PdfTemplate> create(@RequestBody PdfTemplate template) {
        UUID userId = secCtx.getCurrentUserId();
        PdfTemplate toCreate = new PdfTemplate(null, userId, template.name(), template.description(),
                template.documentType(), template.htmlTemplate(), template.cssStyles(),
                false, true, null);
        PdfTemplate saved = service.create(toCreate);
        return ResponseEntity.created(URI.create("/api/v1/pdf-templates/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update PDF template")
    @PutMapping("/{id}")
    public ResponseEntity<PdfTemplate> update(@PathVariable UUID id, @RequestBody PdfTemplate template) {
        UUID userId = secCtx.getCurrentUserId();
        PdfTemplate toUpdate = new PdfTemplate(id, userId, template.name(), template.description(),
                template.documentType(), template.htmlTemplate(), template.cssStyles(),
                false, template.isActive(), null);
        return ResponseEntity.ok(service.update(toUpdate));
    }

    @Operation(summary = "Delete PDF template")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
