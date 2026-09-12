package com.autoapplicant.adapter.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.FirebaseTokenFilter;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.user.CompleteOnboardingUseCase;
import com.autoapplicant.port.in.user.GetUserProfileUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetUserProfileUseCase       getUserProfileUseCase;
    @MockitoBean ResolveLinkedInUserUseCase  resolveLinkedInUser;
    @MockitoBean CompleteOnboardingUseCase   completeOnboardingUseCase;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean SecurityContextHelper      secCtx;
    @MockitoBean AppProperties              appProperties;
    @MockitoBean FirebaseAuth               firebaseAuth;
    @MockitoBean FirebaseTokenFilter        firebaseTokenFilter;

    UUID userId = UUID.randomUUID();

    @Test
    void me_returns_user_info_for_authenticated_user() throws Exception {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
        User user = new User(userId, "alice@example.com", null, null, "firebase-uid",
                UserRole.USER, true, true, Instant.now(), Instant.now());
        when(getUserProfileUseCase.getUser(userId)).thenReturn(Optional.of(user));

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void me_returns_500_when_user_not_in_db() throws Exception {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
        when(getUserProfileUseCase.getUser(userId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isConflict());
    }
}
