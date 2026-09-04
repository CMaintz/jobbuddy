package com.autoapplicant.adapter.web.dto.ai;

/** Provider plus the key itself. Only ever a request body — never a response. */
public record AiCredentialRequest(String provider, String apiKey, String model) {}
