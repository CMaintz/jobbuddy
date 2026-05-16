package com.autoapplicant.adapter.web.dto.auth;

import java.util.UUID;

public record MeResponse(UUID userId, String email, String role) {}
