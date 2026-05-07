package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.JwtTokenProvider;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.user.LoginUserUseCase;
import com.autoapplicant.port.in.user.RegisterUserUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean RegisterUserUseCase registerUseCase;
    @MockBean LoginUserUseCase    loginUseCase;
    @MockBean JwtTokenProvider    jwtProvider;

    UUID   userId = UUID.randomUUID();
    String token  = "test.jwt.token";

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @Test
    void register_returns_200_with_token() throws Exception {
        User saved = new User(userId, "alice@example.com", "hash",
                null, UserRole.USER, false, Instant.now(), Instant.now());
        when(registerUseCase.register("alice@example.com", "password123", "Alice")).thenReturn(saved);
        when(loginUseCase.login("alice@example.com", "password123")).thenReturn(token);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"password123","fullName":"Alice"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void register_returns_400_for_invalid_email() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"password123","fullName":"Alice"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registerUseCase);
    }

    @Test
    void register_returns_400_for_short_password() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"short","fullName":"Alice"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registerUseCase);
    }

    @Test
    void register_returns_400_for_blank_email() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"password123","fullName":"Alice"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns_400_when_email_already_registered() throws Exception {
        when(registerUseCase.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Email already registered"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@example.com","password":"password123","fullName":"Dup"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @Test
    void login_returns_200_with_token() throws Exception {
        when(loginUseCase.login("alice@example.com", "password123")).thenReturn(token);
        Claims claims = mockClaims(userId, "alice@example.com", "USER");
        when(jwtProvider.parseToken(token)).thenReturn(claims);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void login_returns_400_for_missing_email() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"password123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns_400_for_invalid_email_format() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bad","password":"password123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns_400_when_service_throws_invalid_credentials() throws Exception {
        when(loginUseCase.login(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Invalid credentials"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ghost@example.com","password":"wrongpassword"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Claims mockClaims(UUID userId, String email, String role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("email")).thenReturn(email);
        when(claims.get("role")).thenReturn(role);
        return claims;
    }
}
