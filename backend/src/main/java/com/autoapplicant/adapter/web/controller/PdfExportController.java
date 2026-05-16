package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.pdf.PdfRenderingService;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.pdf.PdfExportRequest;
import com.autoapplicant.domain.document.PdfTemplate;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.document.ManagePdfTemplatesUseCase;
import com.autoapplicant.port.in.user.GetUserProfileUseCase;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "PDF Export")
public class PdfExportController {

    private final ManagePdfTemplatesUseCase pdfTemplates;
    private final GetUserProfileUseCase getUserProfile;
    private final UserRepositoryPort userRepo;
    private final PdfRenderingService pdfRenderer;
    private final SecurityContextHelper secCtx;

    public PdfExportController(ManagePdfTemplatesUseCase pdfTemplates,
                               GetUserProfileUseCase getUserProfile,
                               UserRepositoryPort userRepo,
                               PdfRenderingService pdfRenderer,
                               SecurityContextHelper secCtx) {
        this.pdfTemplates = pdfTemplates;
        this.getUserProfile = getUserProfile;
        this.userRepo = userRepo;
        this.pdfRenderer = pdfRenderer;
        this.secCtx = secCtx;
    }

    @PostMapping("/export-pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody PdfExportRequest req) {
        UUID userId = secCtx.getCurrentUserId();

        PdfTemplate template = pdfTemplates.getById(req.pdfTemplateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF template not found"));

        Profile profile = getUserProfile.getProfile(userId).orElse(null);
        User user = userRepo.findById(userId).orElse(null);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("NAME",     profile != null && profile.fullName()    != null ? profile.fullName()    : "");
        placeholders.put("EMAIL",    user    != null && user.email()           != null ? user.email()           : "");
        placeholders.put("PHONE",    profile != null && profile.phone()        != null ? profile.phone()        : "");
        placeholders.put("LOCATION", profile != null && profile.location()     != null ? profile.location()     : "");
        placeholders.put("LINKEDIN", profile != null && profile.linkedinUrl()  != null ? profile.linkedinUrl()  : "");
        placeholders.put("GITHUB",   profile != null && profile.githubUrl()    != null ? profile.githubUrl()    : "");
        placeholders.put("HEADLINE", profile != null && profile.headline()     != null ? profile.headline()     : "");
        placeholders.put("DATE",     LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));

        String rawContent = req.content() != null ? req.content() : "";
        String escapedContent = rawContent
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
        placeholders.put("CONTENT", escapedContent);

        byte[] pdf = pdfRenderer.render(template.htmlTemplate(), template.cssStyles(), placeholders);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document.pdf\"")
                .body(pdf);
    }
}
