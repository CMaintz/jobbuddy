package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.LanguageProficiency;
import com.autoapplicant.domain.user.ProfileLanguage;
import com.autoapplicant.port.out.user.ProfileLanguageRepositoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Profile Languages")
public class ProfileLanguageController {

    private final ProfileLanguageRepositoryPort languageRepo;
    private final SecurityContextHelper secCtx;

    public ProfileLanguageController(ProfileLanguageRepositoryPort languageRepo,
                                     SecurityContextHelper secCtx) {
        this.languageRepo = languageRepo;
        this.secCtx = secCtx;
    }

    public record LanguageRequest(String language, String proficiency, int displayOrder) {}

    @Operation(summary = "List profile languages")
    @GetMapping("/api/v1/profile/languages")
    public ResponseEntity<List<ProfileLanguage>> getLanguages() {
        return ResponseEntity.ok(languageRepo.findByUserId(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Add profile language")
    @PostMapping("/api/v1/profile/languages")
    public ResponseEntity<ProfileLanguage> addLanguage(@RequestBody LanguageRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        LanguageProficiency proficiency = LanguageProficiency.valueOf(req.proficiency().toUpperCase());
        ProfileLanguage lang = new ProfileLanguage(null, userId, req.language(), proficiency,
                req.displayOrder(), null, null);
        return ResponseEntity.ok(languageRepo.save(lang));
    }

    @Operation(summary = "Update profile language")
    @PutMapping("/api/v1/profile/languages/{id}")
    public ResponseEntity<ProfileLanguage> updateLanguage(@PathVariable UUID id,
                                                          @RequestBody LanguageRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        LanguageProficiency proficiency = LanguageProficiency.valueOf(req.proficiency().toUpperCase());
        ProfileLanguage lang = new ProfileLanguage(id, userId, req.language(), proficiency,
                req.displayOrder(), null, null);
        return ResponseEntity.ok(languageRepo.save(lang));
    }

    @Operation(summary = "Delete profile language")
    @DeleteMapping("/api/v1/profile/languages/{id}")
    public ResponseEntity<Void> deleteLanguage(@PathVariable UUID id) {
        languageRepo.deleteByIdAndUserId(id, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
