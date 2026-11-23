package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.user.UpdateUiLanguageRequest;
import com.autoapplicant.domain.user.UiLanguage;
import com.autoapplicant.port.in.user.UpdateUiLanguageUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/language")
@Tag(name = "Users")
public class UiLanguageController {

    private final UpdateUiLanguageUseCase updateLanguage;
    private final SecurityContextHelper secCtx;

    public UiLanguageController(UpdateUiLanguageUseCase updateLanguage, SecurityContextHelper secCtx) {
        this.updateLanguage = updateLanguage;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Set the language the user reads the app in; emails follow it")
    @ApiResponse(responseCode = "204", description = "Language saved")
    @ApiResponse(responseCode = "400", description = "Not a language the app is written in")
    @PutMapping
    public ResponseEntity<Void> update(@Valid @RequestBody UpdateUiLanguageRequest req) {
        UiLanguage language = UiLanguage.fromCode(req.language())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + req.language()));
        updateLanguage.updateLanguage(secCtx.getCurrentUserId(), language);
        return ResponseEntity.noContent().build();
    }
}
