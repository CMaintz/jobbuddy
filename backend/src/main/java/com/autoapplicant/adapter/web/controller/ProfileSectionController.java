package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Master Career Profile")
public class ProfileSectionController {

    private final ManageWorkExperienceUseCase workExpUseCase;
    private final ManageProjectsUseCase projectsUseCase;
    private final ManageEducationUseCase educationUseCase;
    private final ManageCertificationsUseCase certUseCase;
    private final SecurityContextHelper secCtx;

    public ProfileSectionController(ManageWorkExperienceUseCase workExpUseCase,
                                    ManageProjectsUseCase projectsUseCase,
                                    ManageEducationUseCase educationUseCase,
                                    ManageCertificationsUseCase certUseCase,
                                    SecurityContextHelper secCtx) {
        this.workExpUseCase = workExpUseCase;
        this.projectsUseCase = projectsUseCase;
        this.educationUseCase = educationUseCase;
        this.certUseCase = certUseCase;
        this.secCtx = secCtx;
    }

    // ── Work Experience ───────────────────────────────────────────────────────

    @GetMapping("/experience")
    public List<WorkExperience> getExperience() {
        return workExpUseCase.getWorkExperience(secCtx.getCurrentUserId());
    }

    @PostMapping("/experience")
    public WorkExperience addExperience(@RequestBody WorkExperience experience) {
        return workExpUseCase.addWorkExperience(secCtx.getCurrentUserId(), experience);
    }

    @PutMapping("/experience/{id}")
    public WorkExperience updateExperience(@PathVariable UUID id,
                                            @RequestBody WorkExperience experience) {
        return workExpUseCase.updateWorkExperience(secCtx.getCurrentUserId(), id, experience);
    }

    @DeleteMapping("/experience/{id}")
    public ResponseEntity<Void> deleteExperience(@PathVariable UUID id) {
        workExpUseCase.deleteWorkExperience(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    @GetMapping("/projects")
    public List<Project> getProjects() {
        return projectsUseCase.getProjects(secCtx.getCurrentUserId());
    }

    @PostMapping("/projects")
    public Project addProject(@RequestBody Project project) {
        return projectsUseCase.addProject(secCtx.getCurrentUserId(), project);
    }

    @PutMapping("/projects/{id}")
    public Project updateProject(@PathVariable UUID id, @RequestBody Project project) {
        return projectsUseCase.updateProject(secCtx.getCurrentUserId(), id, project);
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectsUseCase.deleteProject(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Education ─────────────────────────────────────────────────────────────

    @GetMapping("/education")
    public List<Education> getEducation() {
        return educationUseCase.getEducation(secCtx.getCurrentUserId());
    }

    @PostMapping("/education")
    public Education addEducation(@RequestBody Education education) {
        return educationUseCase.addEducation(secCtx.getCurrentUserId(), education);
    }

    @PutMapping("/education/{id}")
    public Education updateEducation(@PathVariable UUID id, @RequestBody Education education) {
        return educationUseCase.updateEducation(secCtx.getCurrentUserId(), id, education);
    }

    @DeleteMapping("/education/{id}")
    public ResponseEntity<Void> deleteEducation(@PathVariable UUID id) {
        educationUseCase.deleteEducation(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Certifications ────────────────────────────────────────────────────────

    @GetMapping("/certifications")
    public List<Certification> getCertifications() {
        return certUseCase.getCertifications(secCtx.getCurrentUserId());
    }

    @PostMapping("/certifications")
    public Certification addCertification(@RequestBody Certification certification) {
        return certUseCase.addCertification(secCtx.getCurrentUserId(), certification);
    }

    @DeleteMapping("/certifications/{id}")
    public ResponseEntity<Void> deleteCertification(@PathVariable UUID id) {
        certUseCase.deleteCertification(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
