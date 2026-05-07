package com.autoapplicant.adapter.web.dto.auth;

import java.util.UUID;

public record AuthResponse(String token, UUID userId, String email, String role) {}
