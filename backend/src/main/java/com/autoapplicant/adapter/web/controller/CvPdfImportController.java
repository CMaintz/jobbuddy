package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile Import")
public class CvPdfImportController {

    private final ParseCvUseCase parseCv;
    private final SecurityContextHelper secCtx;

    public CvPdfImportController(ParseCvUseCase parseCv, SecurityContextHelper secCtx) {
        this.parseCv = parseCv;
        this.secCtx = secCtx;
    }

    @PostMapping(value = "/import/cv-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Profile> importCvPdf(@RequestParam("file") MultipartFile file) {
        String extractedText;
        try (PDDocument doc = PDDocument.load(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(doc);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read PDF file: " + e.getMessage(), e);
        }

        Profile parsed = parseCv.parseCvText(secCtx.getCurrentUserId(), extractedText);
        return ResponseEntity.ok(parsed);
    }
}
