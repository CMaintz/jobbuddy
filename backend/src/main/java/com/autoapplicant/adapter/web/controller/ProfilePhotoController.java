package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.port.in.user.ManageProfilePrivateInfoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/profile")
@Tag(name = "User Profile")
public class ProfilePhotoController {

    private final ManageProfilePrivateInfoUseCase privateInfoUseCase;
    private final SecurityContextHelper secCtx;

    public ProfilePhotoController(ManageProfilePrivateInfoUseCase privateInfoUseCase,
                                   SecurityContextHelper secCtx) {
        this.privateInfoUseCase = privateInfoUseCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Upload profile photo")
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        UUID userId = secCtx.getCurrentUserId();
        String url = privateInfoUseCase.uploadPhoto(userId, file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.ok(Map.of("url", url));
    }
}
