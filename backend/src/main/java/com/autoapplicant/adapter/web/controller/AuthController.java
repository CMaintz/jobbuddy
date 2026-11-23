package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.auth.MeResponse;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.user.CompleteOnboardingUseCase;
import com.autoapplicant.port.in.user.GetUserProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

  private final GetUserProfileUseCase userProfileUseCase;
  private final CompleteOnboardingUseCase completeOnboardingUseCase;
  private final SecurityContextHelper securityContext;

  public AuthController(
      GetUserProfileUseCase userProfileUseCase,
      CompleteOnboardingUseCase completeOnboardingUseCase,
      SecurityContextHelper securityContext) {
    this.userProfileUseCase = userProfileUseCase;
    this.completeOnboardingUseCase = completeOnboardingUseCase;
    this.securityContext = securityContext;
  }

  /**
   * Returns the internal user record for the currently authenticated user. The frontend
   * calls this right after signing in to get the internal userId and role.
   */
  @Operation(summary = "Get current authenticated user")
  @GetMapping("/me")
  public ResponseEntity<MeResponse> me() {
    UUID userId = securityContext.getCurrentUserId();
    User user =
        userProfileUseCase
            .getUser(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
    return ResponseEntity.ok(
        new MeResponse(user.id(), user.email(), user.role().name(), user.onboardingComplete()));
  }

  @Operation(summary = "Mark onboarding as complete")
  @PostMapping("/complete-onboarding")
  public ResponseEntity<Void> completeOnboarding() {
    completeOnboardingUseCase.completeOnboarding(securityContext.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }
}
