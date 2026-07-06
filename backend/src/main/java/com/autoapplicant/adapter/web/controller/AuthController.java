package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.auth.MeResponse;
import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.user.CompleteOnboardingUseCase;
import com.autoapplicant.port.in.user.GetUserProfileUseCase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final GetUserProfileUseCase userProfileUseCase;
    private final ResolveLinkedInUserUseCase resolveLinkedInUser;
    private final CompleteOnboardingUseCase completeOnboardingUseCase;
    private final SecurityContextHelper securityContext;
    private final AppProperties appProperties;
    private final FirebaseAuth firebaseAuth;
    private final RestTemplate restTemplate;

    public AuthController(GetUserProfileUseCase userProfileUseCase,
                          ResolveLinkedInUserUseCase resolveLinkedInUser,
                          CompleteOnboardingUseCase completeOnboardingUseCase,
                          SecurityContextHelper securityContext,
                          AppProperties appProperties,
                          FirebaseAuth firebaseAuth) {
        this.userProfileUseCase = userProfileUseCase;
        this.resolveLinkedInUser = resolveLinkedInUser;
        this.completeOnboardingUseCase = completeOnboardingUseCase;
        this.securityContext = securityContext;
        this.appProperties = appProperties;
        this.firebaseAuth = firebaseAuth;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Returns the internal user record for the currently authenticated Firebase user.
     * The frontend calls this right after signing in to get the internal userId and role.
     */
    @Operation(summary = "Get current authenticated user")
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        UUID userId = securityContext.getCurrentUserId();
        User user = userProfileUseCase.getUser(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
        return ResponseEntity.ok(new MeResponse(user.id(), user.email(), user.role().name(), user.onboardingComplete()));
    }

    @Operation(summary = "Mark onboarding as complete")
    @PostMapping("/complete-onboarding")
    public ResponseEntity<Void> completeOnboarding() {
        completeOnboardingUseCase.completeOnboarding(securityContext.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * LinkedIn OAuth callback: exchanges the authorization code, resolves the user,
     * and returns a Firebase Custom Token. The frontend then calls
     * signInWithCustomToken(auth, customToken) to get a proper Firebase ID token.
     */
    @Operation(summary = "LinkedIn OAuth callback, returns Firebase token")
    @PostMapping("/linkedin")
    public ResponseEntity<Map<String, String>> linkedInCallback(
            @RequestBody LinkedInCallbackRequest request) {

        String accessToken = exchangeCodeForToken(request.code(), request.redirectUri());
        Map<String, Object> userInfo = fetchUserInfo(accessToken);

        String sub = (String) userInfo.get("sub");
        String email = (String) userInfo.get("email");
        String givenName = (String) userInfo.getOrDefault("given_name", "");
        String familyName = (String) userInfo.getOrDefault("family_name", "");
        String fullName = (String) userInfo.getOrDefault("name", (givenName + " " + familyName).trim());

        User user = resolveLinkedInUser.resolve(sub, email, fullName);

        try {
            String customToken = firebaseAuth.createCustomToken(
                    user.firebaseUid() != null ? user.firebaseUid() : "linkedin:" + sub,
                    Map.of("role", user.role().name())
            );
            return ResponseEntity.ok(Map.of("customToken", customToken));
        } catch (FirebaseAuthException e) {
            throw new IllegalStateException("Failed to create Firebase custom token", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String exchangeCodeForToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("client_id", appProperties.getLinkedin().getClientId());
        body.add("client_secret", appProperties.getLinkedin().getClientSecret());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
        var response = restTemplate.postForEntity(
                "https://www.linkedin.com/oauth/v2/accessToken", entity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain LinkedIn access token");
        }
        return (String) responseBody.get("access_token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        var response = restTemplate.exchange(
                "https://api.linkedin.com/v2/userinfo",
                org.springframework.http.HttpMethod.GET,
                entity,
                Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("Empty userinfo response from LinkedIn");
        }
        return responseBody;
    }

    record LinkedInCallbackRequest(String code, String redirectUri) {}
}
