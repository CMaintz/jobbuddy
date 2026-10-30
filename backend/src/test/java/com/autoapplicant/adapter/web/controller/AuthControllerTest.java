package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.FirebaseTokenFilter;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.LinkedInIdentity;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.user.CompleteOnboardingUseCase;
import com.autoapplicant.port.in.user.GetUserProfileUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken";
    private static final String USERINFO_URL = "https://api.linkedin.com/v2/userinfo";
    private static final String CALLBACK_BODY = """
            {"code":"auth-code","redirectUri":"https://app.example.com/cb"}
            """;

    @Autowired MockMvc mvc;
    @Autowired AuthController controller;

    @MockitoBean GetUserProfileUseCase       getUserProfileUseCase;
    @MockitoBean ResolveLinkedInUserUseCase  resolveLinkedInUser;
    @MockitoBean CompleteOnboardingUseCase   completeOnboardingUseCase;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean SecurityContextHelper      secCtx;
    @MockitoBean AppProperties              appProperties;
    @MockitoBean FirebaseAuth               firebaseAuth;
    @MockitoBean FirebaseTokenFilter        firebaseTokenFilter;

    UUID userId = UUID.randomUUID();
    MockRestServiceServer linkedIn;

    @BeforeEach
    void setUp() {
        AppProperties.LinkedIn linkedInConfig = new AppProperties.LinkedIn();
        linkedInConfig.setClientId("client-id-1");
        linkedInConfig.setClientSecret("client-secret-1");
        when(appProperties.getLinkedin()).thenReturn(linkedInConfig);
        // The controller builds its own RestTemplate; binding the mock server to that instance is
        // the only way to stand in for LinkedIn without changing production wiring.
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(controller, "restTemplate");
        linkedIn = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void me_returns_user_info_for_authenticated_user() throws Exception {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
        when(getUserProfileUseCase.getUser(userId)).thenReturn(Optional.of(user("firebase-uid")));

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void me_returns_409_when_user_not_in_db() throws Exception {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
        when(getUserProfileUseCase.getUser(userId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isConflict());
    }

    @Test
    void linkedin_callback_without_an_access_token_never_resolves_a_user() throws Exception {
        expectTokenExchange()
                .andRespond(withSuccess("{\"error\":\"invalid_grant\"}", MediaType.APPLICATION_JSON));

        mvc.perform(post("/api/v1/auth/linkedin")
                        .contentType(MediaType.APPLICATION_JSON).content(CALLBACK_BODY))
                .andExpect(status().isConflict());

        verifyNoInteractions(resolveLinkedInUser, firebaseAuth);
        linkedIn.verify();
    }

    @Test
    void linkedin_callback_falls_back_to_the_linkedin_uid_when_the_user_has_no_firebase_uid()
            throws Exception {
        stubLinkedIn("{\"sub\":\"member-7\",\"email\":\"alice@example.com\",\"email_verified\":true}");
        when(resolveLinkedInUser.resolve(any())).thenReturn(user(null));
        when(firebaseAuth.createCustomToken(anyString(), anyMap())).thenReturn("custom-token");

        mvc.perform(post("/api/v1/auth/linkedin")
                        .contentType(MediaType.APPLICATION_JSON).content(CALLBACK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customToken").value("custom-token"));

        verify(firebaseAuth).createCustomToken(eq("linkedin:member-7"), anyMap());
    }

    @Test
    void linkedin_callback_refused_for_a_missing_email_is_403_and_never_mints_a_token()
            throws Exception {
        // The resolver owns the verified-email rule; the controller must hand it the identity
        // unverified and turn its refusal into 403 before any Firebase token exists.
        stubLinkedIn("{\"sub\":\"member-7\"}");
        when(resolveLinkedInUser.resolve(any()))
                .thenThrow(new SecurityException("LinkedIn has not verified this email address"));

        mvc.perform(post("/api/v1/auth/linkedin")
                        .contentType(MediaType.APPLICATION_JSON).content(CALLBACK_BODY))
                .andExpect(status().isForbidden());

        ArgumentCaptor<LinkedInIdentity> identity = ArgumentCaptor.forClass(LinkedInIdentity.class);
        verify(resolveLinkedInUser).resolve(identity.capture());
        assertThat(identity.getValue()).isEqualTo(new LinkedInIdentity("member-7", null, false, ""));
        verifyNoInteractions(firebaseAuth);
    }

    private void stubLinkedIn(String userInfoJson) {
        expectTokenExchange()
                .andRespond(withSuccess("{\"access_token\":\"at-1\"}", MediaType.APPLICATION_JSON));
        linkedIn.expect(requestTo(USERINFO_URL))
                .andExpect(header("Authorization", "Bearer at-1"))
                .andRespond(withSuccess(userInfoJson, MediaType.APPLICATION_JSON));
    }

    private ResponseActions expectTokenExchange() {
        // Each field gets a distinct value so swapping any two of them in the controller fails.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", "auth-code");
        form.add("redirect_uri", "https://app.example.com/cb");
        form.add("client_id", "client-id-1");
        form.add("client_secret", "client-secret-1");
        return linkedIn.expect(requestTo(TOKEN_URL)).andExpect(content().formData(form));
    }

    private User user(String firebaseUid) {
        return new User(userId, "alice@example.com", null, null, firebaseUid,
                UserRole.USER, true, true, Instant.now(), Instant.now());
    }
}
