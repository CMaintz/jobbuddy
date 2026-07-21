package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.document.AnalyzeWritingStyleRequest;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.in.document.AnalyzeWritingStyleUseCase;
import com.autoapplicant.port.in.document.ManageWritingProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/writing-style")
@Tag(name = "Writing Style")
public class WritingProfileController {

    private final ManageWritingProfileUseCase writingProfileUseCase;
    private final AnalyzeWritingStyleUseCase analyzeUseCase;
    private final SecurityContextHelper secCtx;

    public WritingProfileController(ManageWritingProfileUseCase writingProfileUseCase,
                                    AnalyzeWritingStyleUseCase analyzeUseCase,
                                    SecurityContextHelper secCtx) {
        this.writingProfileUseCase = writingProfileUseCase;
        this.analyzeUseCase = analyzeUseCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Get writing style profile")
    @GetMapping
    public ResponseEntity<WritingProfile> get() {
        UUID userId = secCtx.getCurrentUserId();
        return writingProfileUseCase.get(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(new WritingProfile(null, userId,
                        null, null, null, null, null, null, null, null, null, null)));
    }

    @Operation(summary = "Update writing style profile")
    @PutMapping
    public ResponseEntity<WritingProfile> update(@RequestBody WritingProfile profile) {
        UUID userId = secCtx.getCurrentUserId();
        WritingProfile toSave = new WritingProfile(
                profile.id(), userId,
                profile.tone(), profile.vocabularyNotes(),
                profile.phrasingPatterns(), profile.exampleExcerpts(),
                profile.dos(), profile.donts(), profile.structureNotes(),
                profile.lastAnalyzedAt(), profile.createdAt(), profile.updatedAt());
        return ResponseEntity.ok(writingProfileUseCase.save(toSave));
    }

    @Operation(summary = "Analyze writing samples into a proposed style profile (not persisted — save via PUT)")
    @PostMapping("/analyze")
    public ResponseEntity<WritingProfile> analyze(@RequestBody AnalyzeWritingStyleRequest request) {
        UUID userId = secCtx.getCurrentUserId();
        return ResponseEntity.ok(analyzeUseCase.analyze(userId, request.samples(), request.documentIds()));
    }
}
