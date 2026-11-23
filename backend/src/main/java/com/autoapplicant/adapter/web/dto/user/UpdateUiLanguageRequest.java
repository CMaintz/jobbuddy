package com.autoapplicant.adapter.web.dto.user;

import jakarta.validation.constraints.NotBlank;

/** @param language a two-letter code the app is written in, e.g. "da" */
public record UpdateUiLanguageRequest(@NotBlank String language) {}
