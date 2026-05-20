package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.AiProviderPort;
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

    private final AiProviderPort aiProvider;
    private final SecurityContextHelper securityContextHelper;

    public LinkedInImportController(AiProviderPort aiProvider,
                                    SecurityContextHelper securityContextHelper) {
        this.aiProvider = aiProvider;
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

        String systemPrompt = "You are a data extraction assistant. Extract structured information from a LinkedIn profile PDF export. Return ONLY valid JSON, no markdown.";
        String userPrompt = "Extract the following fields from this LinkedIn profile PDF and return as JSON:\n" +
                "{\n" +
                "  \"fullName\": \"\",\n" +
                "  \"headline\": \"\",\n" +
                "  \"summary\": \"\",\n" +
                "  \"location\": \"\",\n" +
                "  \"skills\": [],\n" +
                "  \"experience\": [{\"title\":\"\",\"company\":\"\",\"startDate\":\"\",\"endDate\":\"\",\"description\":\"\"}],\n" +
                "  \"education\": [{\"institution\":\"\",\"degree\":\"\",\"fieldOfStudy\":\"\",\"startDate\":\"\",\"endDate\":\"\"}],\n" +
                "  \"certifications\": [{\"name\":\"\",\"issuer\":\"\",\"issuedAt\":\"\"}]\n" +
                "}\n\nPDF content:\n" + extractedText;

        PromptComposition composition = new PromptComposition(
                systemPrompt,
                userPrompt,
                "",
                "",
                "",
                "",
                userPrompt
        );

        String result = aiProvider.generate(composition);
        return ResponseEntity.ok(result);
    }
}
