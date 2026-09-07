package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.port.in.user.ParseLinkedInProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Profile Import")
public class LinkedInImportController {

    private final ParseLinkedInProfileUseCase parseLinkedIn;
    private final SecurityContextHelper securityContextHelper;

    public LinkedInImportController(ParseLinkedInProfileUseCase parseLinkedIn,
                                    SecurityContextHelper securityContextHelper) {
        this.parseLinkedIn = parseLinkedIn;
        this.securityContextHelper = securityContextHelper;
    }

    @Operation(summary = "Import profile from LinkedIn PDF export")
    @PostMapping(value = "/import/linkedin-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importLinkedInPdf(@RequestParam("file") MultipartFile file) {
        String extractedText;
        try (PDDocument doc = PDDocument.load(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(doc);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read PDF file: " + e.getMessage(), e);
        }

        return ResponseEntity.ok(parseLinkedIn.parseProfileFromText(
                securityContextHelper.getCurrentUserId(), extractedText));
    }
}
