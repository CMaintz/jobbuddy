package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.UserPreferences;
import java.util.List;
import java.util.Map;
import com.autoapplicant.port.in.user.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final DeleteUserAccountUseCase deleteAccount;
    private final ExportUserDataUseCase exportUserData;
    private final SecurityContextHelper secCtx;

    public UserController(GetUserProfileUseCase getProfile, UpdateUserProfileUseCase updateProfile,
                          UpdatePreferencesUseCase updatePreferences,
                          DeleteUserAccountUseCase deleteAccount,
                          ExportUserDataUseCase exportUserData,
                          SecurityContextHelper secCtx) {
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
        this.updatePreferences = updatePreferences;
        this.deleteAccount = deleteAccount;
        this.exportUserData = exportUserData;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Get current user profile")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Profile not found"))
    @GetMapping("/profile")
    public ResponseEntity<Profile> getProfile() {
        return getProfile.getProfile(secCtx.getCurrentUserId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update current user profile")
    @PutMapping("/profile")
    public ResponseEntity<Profile> updateProfile(@RequestBody Profile profile) {
        return ResponseEntity.ok(updateProfile.updateProfile(secCtx.getCurrentUserId(), profile));
    }

    @Operation(summary = "Get current user preferences")
    @GetMapping("/preferences")
    public ResponseEntity<UserPreferences> getPreferences() {
        java.util.UUID userId = secCtx.getCurrentUserId();
        return ResponseEntity.ok(updatePreferences.getPreferences(userId)
                .orElse(new UserPreferences(null, userId,
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), null, null, null,
                        false, "DAILY", null, null, null)));
    }

    @Operation(summary = "Update current user preferences")
    @PutMapping("/preferences")
    public ResponseEntity<UserPreferences> updatePreferences(@RequestBody UserPreferences prefs) {
        return ResponseEntity.ok(updatePreferences.updatePreferences(secCtx.getCurrentUserId(), prefs));
    }

    @Operation(summary = "Export all user data (GDPR Article 20 — Right to Data Portability)",
               description = "Returns all personal data for the authenticated user in JSON format.")
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportData() {
        return ResponseEntity.ok(exportUserData.exportUserData(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Delete current user account (GDPR Article 17 — Right to Erasure)",
               description = "Permanently deletes all data for the authenticated user from the database and from Firebase. This action is irreversible.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Account deleted successfully"))
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount() {
        deleteAccount.deleteAccount(secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
