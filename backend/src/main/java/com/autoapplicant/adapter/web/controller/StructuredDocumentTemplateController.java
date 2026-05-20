package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.document.StructuredDocumentTemplate;
import com.autoapplicant.port.in.document.GetStructuredDocumentTemplatesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/structured-document-templates")
@Tag(name = "Structured Document Templates")
public class StructuredDocumentTemplateController {

    private final GetStructuredDocumentTemplatesUseCase templates;

    public StructuredDocumentTemplateController(GetStructuredDocumentTemplatesUseCase templates) {
        this.templates = templates;
    }

    @Operation(summary = "List structured document templates")
    @GetMapping
    public ResponseEntity<List<StructuredDocumentTemplate>> list() {
        return ResponseEntity.ok(templates.getActiveTemplates());
    }
}
