package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.user.*;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetUserProfileUseCase        getProfile;
    @MockitoBean UpdateUserProfileUseCase     updateProfile;
    @MockitoBean UpdatePreferencesUseCase     updatePreferences;
    @MockitoBean DeleteUserAccountUseCase     deleteAccount;
    @MockitoBean ExportUserDataUseCase        exportUserData;
    @MockitoBean SecurityContextHelper        secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase   resolveLinkedInUser;
    @MockitoBean FirebaseAuth                 firebaseAuth;

    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void get_profile_returns_current_users_profile() throws Exception {
        when(getProfile.getProfile(userId)).thenReturn(Optional.of(profile("Backend engineer")));

        mvc.perform(get("/api/v1/users/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.headline").value("Backend engineer"))
                .andExpect(jsonPath("$.languages", Matchers.contains("Danish", "English")));
    }

    @Test
    void get_profile_returns_404_when_missing() throws Exception {
        when(getProfile.getProfile(userId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/users/me/profile"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_profile_accepts_patch_and_passes_body() throws Exception {
        when(updateProfile.updateProfile(eq(userId), any(Profile.class)))
                .thenReturn(profile("Staff engineer"));

        mvc.perform(patch("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"headline":"Staff engineer","yearsExperience":8}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Staff engineer"));

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(updateProfile).updateProfile(eq(userId), captor.capture());
        assertThat(captor.getValue().headline()).isEqualTo("Staff engineer");
        assertThat(captor.getValue().yearsExperience()).isEqualTo(8);
    }

    @Test
    void update_profile_also_accepts_put() throws Exception {
        when(updateProfile.updateProfile(eq(userId), any(Profile.class)))
                .thenReturn(profile("Staff engineer"));

        mvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"headline":"Staff engineer"}
                                """))
                .andExpect(status().isOk());

        verify(updateProfile).updateProfile(eq(userId), any(Profile.class));
    }

    @Test
    void get_preferences_returns_defaults_when_none_saved() throws Exception {
        when(updatePreferences.getPreferences(userId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/users/me/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.notificationFrequency").value("DAILY"))
                .andExpect(jsonPath("$.notificationEnabled").value(false))
                .andExpect(jsonPath("$.preferredLocations", hasSize(0)));
    }

    @Test
    void update_preferences_passes_body_for_current_user() throws Exception {
        UserPreferences saved = new UserPreferences(UUID.randomUUID(), userId,
                List.of("Copenhagen"), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), 50000, null, 30,
                true, "WEEKLY", 5, Instant.now(), Instant.now());
        when(updatePreferences.updatePreferences(eq(userId), any(UserPreferences.class)))
                .thenReturn(saved);

        mvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredLocations":["Copenhagen"],"notificationFrequency":"WEEKLY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLocations[0]").value("Copenhagen"))
                .andExpect(jsonPath("$.notificationFrequency").value("WEEKLY"));

        ArgumentCaptor<UserPreferences> captor = ArgumentCaptor.forClass(UserPreferences.class);
        verify(updatePreferences).updatePreferences(eq(userId), captor.capture());
        assertThat(captor.getValue().preferredLocations()).containsExactly("Copenhagen");
    }

    @Test
    void export_returns_use_case_payload() throws Exception {
        when(exportUserData.exportUserData(userId))
                .thenReturn(Map.of("profile", Map.of("headline", "Dev")));

        mvc.perform(get("/api/v1/users/me/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.headline").value("Dev"));
    }

    @Test
    void delete_account_returns_204_and_deletes_current_user() throws Exception {
        mvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isNoContent());

        verify(deleteAccount).deleteAccount(userId);
    }

    private Profile profile(String headline) {
        return new Profile(UUID.randomUUID(), userId, headline, null, 5,
                List.of("Danish", "English"), List.of(), null, null, null, null, null,
                Instant.now(), Instant.now());
    }
}
