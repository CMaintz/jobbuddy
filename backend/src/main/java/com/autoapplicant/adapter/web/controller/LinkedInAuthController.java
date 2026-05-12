package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.JwtTokenProvider;
import com.autoapplicant.adapter.web.dto.auth.AuthResponse;
import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class LinkedInAuthController {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    public LinkedInAuthController(UserRepositoryPort userRepo,
                                  ProfileRepositoryPort profileRepo,
                                  JwtTokenProvider jwtTokenProvider,
                                  AppProperties appProperties) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.jwtTokenProvider = jwtTokenProvider;
        this.appProperties = appProperties;
        this.restTemplate = new RestTemplate();
    }

    record LinkedInCallbackRequest(String code, String redirectUri) {}

    @PostMapping("/linkedin")
    public ResponseEntity<AuthResponse> linkedInCallback(@RequestBody LinkedInCallbackRequest request) {
        // Step 1: Exchange code for access token
        String accessToken = exchangeCodeForToken(request.code(), request.redirectUri());

        // Step 2: Fetch userinfo from LinkedIn OpenID Connect endpoint
        Map<String, Object> userInfo = fetchUserInfo(accessToken);

        String sub = (String) userInfo.get("sub");
        String email = (String) userInfo.get("email");
        String givenName = (String) userInfo.getOrDefault("given_name", "");
        String familyName = (String) userInfo.getOrDefault("family_name", "");
        String fullName = ((String) userInfo.getOrDefault("name", (givenName + " " + familyName).trim()));

        // Step 3: Find-or-create user
        User user = resolveUser(sub, email, fullName);

        // Step 4: Generate JWT and return
        String token = jwtTokenProvider.generateToken(user.id(), user.email(), user.role());
        return ResponseEntity.ok(new AuthResponse(token, user.id(), user.email(), user.role().name()));
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
        ResponseEntity<Map> response = restTemplate.postForEntity(
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

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.linkedin.com/v2/userinfo",
                org.springframework.http.HttpMethod.GET,
                entity,
                Map.class);

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Empty userinfo response from LinkedIn");
        }
        return body;
    }

    private User resolveUser(String sub, String email, String fullName) {
        // Try to find by LinkedIn ID first
        Optional<User> byLinkedinId = userRepo.findByLinkedinId(sub);
        if (byLinkedinId.isPresent()) {
            return byLinkedinId.get();
        }

        // Try to find by email (link accounts)
        Optional<User> byEmail = userRepo.findByEmail(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            User updated = new User(existing.id(), existing.email(), existing.passwordHash(),
                    existing.googleId(), sub, existing.role(), existing.emailVerified(),
                    existing.createdAt(), existing.updatedAt());
            return userRepo.save(updated);
        }

        // Create new user
        User newUser = new User(null, email, null, null, sub, UserRole.USER, true, null, null);
        User savedUser = userRepo.save(newUser);

        // Create initial profile
        Profile profile = new Profile(null, savedUser.id(), fullName, null, null,
                null, null, null, null, null, null,
                List.of(), List.of(), List.of(),
                null, null, "DKK", null, null, null, null);
        profileRepo.save(profile);

        return savedUser;
    }
}
