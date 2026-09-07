package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Master Career Profile")
public class ProfileSectionController {

    public record FullProfileResponse(
            Profile profile,
            List<WorkExperience> experience,
            List<Education> education,
            List<Project> projects,
            List<Certification> certifications,
            List<SpokenLanguage> languages,
            List<ProfileSocial> socials,
            List<ProfileStrength> strengths
    ) {}

    private final ManageWorkExperienceUseCase workExpUseCase;
    private final ManageProjectsUseCase projectsUseCase;
    private final ManageEducationUseCase educationUseCase;
    private final ManageCertificationsUseCase certUseCase;
    private final GetUserProfileUseCase profileUseCase;
    private final ManageProfileSocialUseCase socialUseCase;
    private final ManageProfileStrengthUseCase strengthUseCase;
    private final ManageSpokenLanguagesUseCase languageUseCase;
    private final SecurityContextHelper secCtx;

    public ProfileSectionController(ManageWorkExperienceUseCase workExpUseCase,
                                    ManageProjectsUseCase projectsUseCase,
                                    ManageEducationUseCase educationUseCase,
                                    ManageCertificationsUseCase certUseCase,
                                    GetUserProfileUseCase profileUseCase,
                                    ManageProfileSocialUseCase socialUseCase,
                                    ManageProfileStrengthUseCase strengthUseCase,
                                    ManageSpokenLanguagesUseCase languageUseCase,
                                    SecurityContextHelper secCtx) {
        this.workExpUseCase = workExpUseCase;
        this.projectsUseCase = projectsUseCase;
        this.educationUseCase = educationUseCase;
        this.certUseCase = certUseCase;
        this.profileUseCase = profileUseCase;
        this.socialUseCase = socialUseCase;
        this.strengthUseCase = strengthUseCase;
        this.languageUseCase = languageUseCase;
        this.secCtx = secCtx;
    }

    // ── Full profile (resume builder prefill) ─────────────────────────────────

    @Operation(summary = "Get all non-PII resume data in one call")
    @GetMapping("/full")
    public FullProfileResponse getFullProfile() {
        UUID userId = secCtx.getCurrentUserId();
        return new FullProfileResponse(
                profileUseCase.getProfile(userId).orElse(null),
                workExpUseCase.getWorkExperience(userId),
                educationUseCase.getEducation(userId),
                projectsUseCase.getProjects(userId),
                certUseCase.getCertifications(userId),
                languageUseCase.getLanguages(userId),
                socialUseCase.getSocials(userId),
                strengthUseCase.getStrengths(userId)
        );
    }

    // ── Work Experience ───────────────────────────────────────────────────────

    @Operation(summary = "List work experience entries")
    @GetMapping("/experience")
    public List<WorkExperience> getExperience() {
        return workExpUseCase.getWorkExperience(secCtx.getCurrentUserId());
    }

    @Operation(summary = "Add work experience entry")
    @ApiResponse(responseCode = "201", description = "Entry created")
    @PostMapping("/experience")
    public ResponseEntity<WorkExperience> addExperience(@RequestBody WorkExperience experience) {
        WorkExperience saved = workExpUseCase.addWorkExperience(secCtx.getCurrentUserId(), experience);
        return ResponseEntity.created(URI.create("/api/v1/profile/experience/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update work experience entry")
    @PutMapping("/experience/{id}")
    public WorkExperience updateExperience(@PathVariable UUID id,
                                            @RequestBody WorkExperience experience) {
        return workExpUseCase.updateWorkExperience(secCtx.getCurrentUserId(), id, experience);
    }

    @Operation(summary = "Delete work experience entry")
    @DeleteMapping("/experience/{id}")
    public ResponseEntity<Void> deleteExperience(@PathVariable UUID id) {
        workExpUseCase.deleteWorkExperience(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    @Operation(summary = "List projects")
    @GetMapping("/projects")
    public List<Project> getProjects() {
        return projectsUseCase.getProjects(secCtx.getCurrentUserId());
    }

    @Operation(summary = "Add project")
    @ApiResponse(responseCode = "201", description = "Project created")
    @PostMapping("/projects")
    public ResponseEntity<Project> addProject(@RequestBody Project project) {
        Project saved = projectsUseCase.addProject(secCtx.getCurrentUserId(), project);
        return ResponseEntity.created(URI.create("/api/v1/profile/projects/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update project")
    @PutMapping("/projects/{id}")
    public Project updateProject(@PathVariable UUID id, @RequestBody Project project) {
        return projectsUseCase.updateProject(secCtx.getCurrentUserId(), id, project);
    }

    @Operation(summary = "Delete project")
    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectsUseCase.deleteProject(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Education ─────────────────────────────────────────────────────────────

    @Operation(summary = "List education entries")
    @GetMapping("/education")
    public List<Education> getEducation() {
        return educationUseCase.getEducation(secCtx.getCurrentUserId());
    }

    @Operation(summary = "Add education entry")
    @ApiResponse(responseCode = "201", description = "Entry created")
    @PostMapping("/education")
    public ResponseEntity<Education> addEducation(@RequestBody Education education) {
        Education saved = educationUseCase.addEducation(secCtx.getCurrentUserId(), education);
        return ResponseEntity.created(URI.create("/api/v1/profile/education/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update education entry")
    @PutMapping("/education/{id}")
    public Education updateEducation(@PathVariable UUID id, @RequestBody Education education) {
        return educationUseCase.updateEducation(secCtx.getCurrentUserId(), id, education);
    }

    @Operation(summary = "Delete education entry")
    @DeleteMapping("/education/{id}")
    public ResponseEntity<Void> deleteEducation(@PathVariable UUID id) {
        educationUseCase.deleteEducation(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Certifications ────────────────────────────────────────────────────────

    @Operation(summary = "List certifications")
    @GetMapping("/certifications")
    public List<Certification> getCertifications() {
        return certUseCase.getCertifications(secCtx.getCurrentUserId());
    }

    @Operation(summary = "Add certification")
    @ApiResponse(responseCode = "201", description = "Certification created")
    @PostMapping("/certifications")
    public ResponseEntity<Certification> addCertification(@RequestBody Certification certification) {
        Certification saved = certUseCase.addCertification(secCtx.getCurrentUserId(), certification);
        return ResponseEntity.created(URI.create("/api/v1/profile/certifications/" + saved.id())).body(saved);
    }

    @Operation(summary = "Delete certification")
    @DeleteMapping("/certifications/{id}")
    public ResponseEntity<Void> deleteCertification(@PathVariable UUID id) {
        certUseCase.deleteCertification(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
