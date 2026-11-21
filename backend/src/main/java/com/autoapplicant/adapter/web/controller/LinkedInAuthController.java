package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.auth.LinkedInIdentity;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * LinkedIn sign-in, which ends in a Firebase custom token. Hosted only: the desktop profile has
 * no Firebase project and signs its one user in with the shell's token instead.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
@Profile("!desktop")
public class LinkedInAuthController {

  /** LinkedIn answers both calls with a JSON object; typed so the body needs no unchecked cast. */
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {};

  private final ResolveLinkedInUserUseCase resolveLinkedInUser;
  private final AppProperties appProperties;
  private final FirebaseAuth firebaseAuth;
  private final RestTemplate restTemplate;

  public LinkedInAuthController(
      ResolveLinkedInUserUseCase resolveLinkedInUser,
      AppProperties appProperties,
      FirebaseAuth firebaseAuth) {
    this.resolveLinkedInUser = resolveLinkedInUser;
    this.appProperties = appProperties;
    this.firebaseAuth = firebaseAuth;
    this.restTemplate = new RestTemplate();
  }

  /**
   * LinkedIn OAuth callback: exchanges the authorization code, resolves the user, and returns a
   * Firebase Custom Token. The frontend then calls signInWithCustomToken(auth, customToken) to get
   * a proper Firebase ID token.
   */
  @Operation(summary = "LinkedIn OAuth callback, returns Firebase token")
  @ApiResponse(
      responseCode = "403",
      description = "LinkedIn has not verified the email and no account is linked yet")
  @ApiResponse(
      responseCode = "409",
      description = "ACCOUNT_EXISTS: another account already holds this email")
  @PostMapping("/linkedin")
  public ResponseEntity<Map<String, String>> linkedInCallback(
      @RequestBody LinkedInCallbackRequest request) {

    String accessToken = exchangeCodeForToken(request.code(), request.redirectUri());
    LinkedInIdentity identity = LinkedInUserInfo.toIdentity(fetchUserInfo(accessToken));

    User user = resolveLinkedInUser.resolve(identity);

    try {
      String customToken =
          firebaseAuth.createCustomToken(
              user.firebaseUid() != null ? user.firebaseUid() : identity.fallbackFirebaseUid(),
              Map.of("role", user.role().name()));
      return ResponseEntity.ok(Map.of("customToken", customToken));
    } catch (FirebaseAuthException e) {
      throw new IllegalStateException("Failed to create Firebase custom token", e);
    }
  }

  private String exchangeCodeForToken(String code, String redirectUri) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    HttpEntity<MultiValueMap<String, String>> entity =
        new HttpEntity<>(tokenRequestForm(code, redirectUri), headers);
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            "https://www.linkedin.com/oauth/v2/accessToken", HttpMethod.POST, entity, JSON_OBJECT);

    Map<String, Object> responseBody = response.getBody();
    if (responseBody == null || !responseBody.containsKey("access_token")) {
      throw new IllegalStateException("Failed to obtain LinkedIn access token");
    }
    return (String) responseBody.get("access_token");
  }

  private MultiValueMap<String, String> tokenRequestForm(String code, String redirectUri) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("redirect_uri", redirectUri);
    form.add("client_id", appProperties.getLinkedin().getClientId());
    form.add("client_secret", appProperties.getLinkedin().getClientSecret());
    return form;
  }

  private Map<String, Object> fetchUserInfo(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + accessToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            "https://api.linkedin.com/v2/userinfo", HttpMethod.GET, entity, JSON_OBJECT);

    Map<String, Object> responseBody = response.getBody();
    if (responseBody == null) {
      throw new IllegalStateException("Empty userinfo response from LinkedIn");
    }
    return responseBody;
  }

  record LinkedInCallbackRequest(String code, String redirectUri) {}
}
