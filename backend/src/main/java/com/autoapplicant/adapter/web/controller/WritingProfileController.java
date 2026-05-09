package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/writing-style")
@Tag(name = "Writing Style")
public class WritingProfileController {

    private final WritingProfileRepositoryPort repo;
    private final SecurityContextHelper secCtx;

    public WritingProfileController(WritingProfileRepositoryPort repo, SecurityContextHelper secCtx) {
        this.repo = repo;
        this.secCtx = secCtx;
    }

    @GetMapping
    public ResponseEntity<WritingProfile> get() {
        UUID userId = secCtx.getCurrentUserId();
        return repo.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(new WritingProfile(null, userId,
                        null, null, null, null, null, null, null)));
    }

    @PutMapping
    public ResponseEntity<WritingProfile> update(@RequestBody WritingProfile profile) {
        UUID userId = secCtx.getCurrentUserId();
        WritingProfile toSave = new WritingProfile(
                profile.id(), userId,
                profile.tone(), profile.vocabularyNotes(),
                profile.phrasingPatterns(), profile.exampleExcerpts(),
                profile.lastAnalyzedAt(), profile.createdAt(), profile.updatedAt());
        return ResponseEntity.ok(repo.save(toSave));
    }
}
