package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.pdf.PdfRenderingService;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.pdf.StructuredApplicationRequest;
import com.autoapplicant.adapter.web.dto.pdf.StructuredDocumentExportRequest;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.document.BuildApplicationDocumentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "PDF Export")
public class PdfExportController {

    private final PdfRenderingService pdfRenderer;
    private final BuildApplicationDocumentUseCase buildAppDocument;
    private final SecurityContextHelper secCtx;

    public PdfExportController(PdfRenderingService pdfRenderer,
                               BuildApplicationDocumentUseCase buildAppDocument,
                               SecurityContextHelper secCtx) {
        this.pdfRenderer = pdfRenderer;
        this.buildAppDocument = buildAppDocument;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Export structured document as PDF")
    @PostMapping("/export-structured-pdf")
    public ResponseEntity<byte[]> exportStructuredPdf(@RequestBody StructuredDocumentExportRequest req) {
        byte[] pdf = pdfRenderer.renderStructured(req.document());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document.pdf\"")
                .body(pdf);
    }

    @Operation(summary = "Build structured application document model")
    @PostMapping("/structured-application")
    public ResponseEntity<StructuredDocument> structuredApplication(@RequestBody StructuredApplicationRequest req) {
        return ResponseEntity.ok(buildAppDocument.buildApplicationDocument(
                secCtx.getCurrentUserId(), req.documentType(), req.content(), req.templateId(),
                Boolean.TRUE.equals(req.showProfileImage()),
                req.theme() != null ? req.theme().toTheme() : null));
    }
}
