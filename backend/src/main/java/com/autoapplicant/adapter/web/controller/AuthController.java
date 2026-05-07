package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.JwtTokenProvider;
import com.autoapplicant.adapter.web.dto.auth.AuthResponse;
import com.autoapplicant.adapter.web.dto.auth.LoginRequest;
import com.autoapplicant.adapter.web.dto.auth.RegisterRequest;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.user.LoginUserUseCase;
import com.autoapplicant.port.in.user.RegisterUserUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final RegisterUserUseCase registerUseCase;
    private final LoginUserUseCase loginUseCase;
    private final JwtTokenProvider jwtProvider;

    public AuthController(RegisterUserUseCase registerUseCase, LoginUserUseCase loginUseCase,
                          JwtTokenProvider jwtProvider) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = registerUseCase.register(req.email(), req.password(), req.fullName());
        String token = loginUseCase.login(req.email(), req.password());
        return ResponseEntity.ok(new AuthResponse(token, user.id(), user.email(), user.role().name()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        String token = loginUseCase.login(req.email(), req.password());
        var claims = jwtProvider.parseToken(token);
        return ResponseEntity.ok(new AuthResponse(token,
                java.util.UUID.fromString(claims.getSubject()),
                (String) claims.get("email"), (String) claims.get("role")));
    }
}
