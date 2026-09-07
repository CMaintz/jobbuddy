package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.ProfileStrength;
import com.autoapplicant.port.in.user.ManageProfileStrengthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/strengths")
@Tag(name = "Profile Strengths")
public class ProfileStrengthController {

    private final ManageProfileStrengthUseCase useCase;
    private final SecurityContextHelper secCtx;

    public ProfileStrengthController(ManageProfileStrengthUseCase useCase,
                                     SecurityContextHelper secCtx) {
        this.useCase = useCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List strengths")
    @GetMapping
    public List<ProfileStrength> getStrengths() {
        return useCase.getStrengths(secCtx.getCurrentUserId());
    }

    @Operation(summary = "Add strength")
    @ApiResponse(responseCode = "201", description = "Strength added")
    @PostMapping
    public ResponseEntity<ProfileStrength> addStrength(@RequestBody ProfileStrength strength) {
        ProfileStrength saved = useCase.addStrength(secCtx.getCurrentUserId(), strength);
        return ResponseEntity.created(URI.create("/api/v1/profile/strengths/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update strength")
    @PutMapping("/{id}")
    public ProfileStrength updateStrength(@PathVariable UUID id,
                                           @RequestBody ProfileStrength strength) {
        return useCase.updateStrength(secCtx.getCurrentUserId(), id, strength);
    }

    @Operation(summary = "Delete strength")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStrength(@PathVariable UUID id) {
        useCase.deleteStrength(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reorder strengths")
    @PutMapping("/reorder")
    public List<ProfileStrength> reorderStrengths(@RequestBody List<ProfileStrength> ordered) {
        return useCase.reorderStrengths(secCtx.getCurrentUserId(), ordered);
    }
}
