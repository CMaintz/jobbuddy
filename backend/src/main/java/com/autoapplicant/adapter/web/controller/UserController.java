package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.in.user.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "User Profile")
public class UserController {

    private final GetUserProfileUseCase getProfile;
    private final UpdateUserProfileUseCase updateProfile;
    private final UpdatePreferencesUseCase updatePreferences;
    private final SecurityContextHelper secCtx;

    public UserController(GetUserProfileUseCase getProfile, UpdateUserProfileUseCase updateProfile,
                          UpdatePreferencesUseCase updatePreferences, SecurityContextHelper secCtx) {
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
        this.updatePreferences = updatePreferences;
        this.secCtx = secCtx;
    }

    @GetMapping("/profile")
    public ResponseEntity<Profile> getProfile() {
        return getProfile.getProfile(secCtx.getCurrentUserId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profile")
    public ResponseEntity<Profile> updateProfile(@RequestBody Profile profile) {
        return ResponseEntity.ok(updateProfile.updateProfile(secCtx.getCurrentUserId(), profile));
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserPreferences> updatePreferences(@RequestBody UserPreferences prefs) {
        return ResponseEntity.ok(updatePreferences.updatePreferences(secCtx.getCurrentUserId(), prefs));
    }
}
