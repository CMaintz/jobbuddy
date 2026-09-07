package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.LanguageProficiency;
import com.autoapplicant.domain.user.SpokenLanguage;
import com.autoapplicant.port.in.user.ManageSpokenLanguagesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/languages")
@Tag(name = "Spoken Languages")
public class SpokenLanguageController {

    private final ManageSpokenLanguagesUseCase languageUseCase;
    private final SecurityContextHelper secCtx;

    public SpokenLanguageController(ManageSpokenLanguagesUseCase languageUseCase,
                                    SecurityContextHelper secCtx) {
        this.languageUseCase = languageUseCase;
        this.secCtx = secCtx;
    }

    public record LanguageRequest(String language, String proficiency, int displayOrder) {}

    @Operation(summary = "List spoken languages on profile")
    @GetMapping
    public ResponseEntity<List<SpokenLanguage>> getLanguages() {
        return ResponseEntity.ok(languageUseCase.getLanguages(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Add spoken language to profile")
    @ApiResponse(responseCode = "201", description = "Language added")
    @PostMapping
    public ResponseEntity<SpokenLanguage> addLanguage(@RequestBody LanguageRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        LanguageProficiency proficiency = LanguageProficiency.valueOf(req.proficiency().toUpperCase());
        SpokenLanguage lang = new SpokenLanguage(null, userId, req.language(), proficiency,
                req.displayOrder(), null, null);
        SpokenLanguage saved = languageUseCase.save(lang);
        return ResponseEntity.created(URI.create("/api/v1/profile/languages/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update spoken language")
    @PutMapping("/{id}")
    public ResponseEntity<SpokenLanguage> updateLanguage(@PathVariable UUID id,
                                                         @RequestBody LanguageRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        LanguageProficiency proficiency = LanguageProficiency.valueOf(req.proficiency().toUpperCase());
        SpokenLanguage lang = new SpokenLanguage(id, userId, req.language(), proficiency,
                req.displayOrder(), null, null);
        return ResponseEntity.ok(languageUseCase.save(lang));
    }

    @Operation(summary = "Delete spoken language")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLanguage(@PathVariable UUID id) {
        languageUseCase.delete(id, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
