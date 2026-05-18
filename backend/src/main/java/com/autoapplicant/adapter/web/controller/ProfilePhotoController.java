package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/profile")
@Tag(name = "User Profile")
public class ProfilePhotoController {

    private final ProfileRepositoryPort profileRepo;
    private final SecurityContextHelper secCtx;

    public ProfilePhotoController(ProfileRepositoryPort profileRepo, SecurityContextHelper secCtx) {
        this.profileRepo = profileRepo;
        this.secCtx = secCtx;
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        UUID userId = secCtx.getCurrentUserId();
        String ext = resolveExtension(file.getOriginalFilename());
        String filename = userId.toString() + "." + ext;

        Path dir = Path.of("uploads", "profile-photos");
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(filename).toAbsolutePath());

        String url = "/uploads/profile-photos/" + filename;

        profileRepo.findByUserId(userId).ifPresent(profile ->
                profileRepo.save(new Profile(
                        profile.id(), profile.userId(), profile.fullName(), profile.headline(),
                        profile.summary(), profile.location(), profile.municipality(),
                        profile.linkedinUrl(), profile.githubUrl(), profile.websiteUrl(),
                        profile.phone(), url, profile.yearsExperience(),
                        profile.skills(), profile.technologies(), profile.languages(),
                        profile.desiredSalaryMin(), profile.desiredSalaryMax(), profile.desiredCurrency(),
                        profile.remotePreference(), profile.employmentTypePreference(),
                        profile.createdAt(), profile.updatedAt()))
        );

        return ResponseEntity.ok(Map.of("url", url));
    }

    private static String resolveExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return ext.matches("jpg|jpeg|png|webp|gif") ? ext : "jpg";
    }
}
