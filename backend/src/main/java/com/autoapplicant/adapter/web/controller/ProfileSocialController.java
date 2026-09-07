package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.port.in.user.ManageProfileSocialUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/socials")
@Tag(name = "Profile Socials")
public class ProfileSocialController {

    private final ManageProfileSocialUseCase useCase;
    private final SecurityContextHelper secCtx;

    public ProfileSocialController(ManageProfileSocialUseCase useCase,
                                   SecurityContextHelper secCtx) {
        this.useCase = useCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List social links")
    @GetMapping
    public List<ProfileSocial> getSocials() {
        return useCase.getSocials(secCtx.getCurrentUserId());
    }

    @Operation(summary = "Add social link")
    @ApiResponse(responseCode = "201", description = "Social link added")
    @PostMapping
    public ResponseEntity<ProfileSocial> addSocial(@RequestBody ProfileSocial social) {
        ProfileSocial saved = useCase.addSocial(secCtx.getCurrentUserId(), social);
        return ResponseEntity.created(URI.create("/api/v1/profile/socials/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update social link")
    @PutMapping("/{id}")
    public ProfileSocial updateSocial(@PathVariable UUID id,
                                       @RequestBody ProfileSocial social) {
        return useCase.updateSocial(secCtx.getCurrentUserId(), id, social);
    }

    @Operation(summary = "Delete social link")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSocial(@PathVariable UUID id) {
        useCase.deleteSocial(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reorder social links")
    @PutMapping("/reorder")
    public List<ProfileSocial> reorderSocials(@RequestBody List<ProfileSocial> ordered) {
        return useCase.reorderSocials(secCtx.getCurrentUserId(), ordered);
    }
}
