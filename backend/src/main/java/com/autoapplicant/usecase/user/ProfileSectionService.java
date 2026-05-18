package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.out.user.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileSectionService implements
        ManageWorkExperienceUseCase,
        ManageProjectsUseCase,
        ManageEducationUseCase,
        ManageCertificationsUseCase {

    private final WorkExperienceRepositoryPort workExpRepo;
    private final ProjectRepositoryPort projectRepo;
    private final EducationRepositoryPort educationRepo;
    private final CertificationRepositoryPort certRepo;

    public ProfileSectionService(WorkExperienceRepositoryPort workExpRepo,
                                 ProjectRepositoryPort projectRepo,
                                 EducationRepositoryPort educationRepo,
                                 CertificationRepositoryPort certRepo) {
        this.workExpRepo = workExpRepo;
        this.projectRepo = projectRepo;
        this.educationRepo = educationRepo;
        this.certRepo = certRepo;
    }

    // ── Work Experience ───────────────────────────────────────────────────────

    @Override
    public WorkExperience addWorkExperience(UUID userId, WorkExperience experience) {
        WorkExperience withUser = new WorkExperience(null, userId, experience.companyName(),
                experience.title(), experience.location(), experience.description(),
                experience.startDate(), experience.endDate(), experience.isCurrent(),
                experience.technologies(), experience.achievements(),
                experience.displayOrder(), null, null, experience.skills());
        return workExpRepo.save(withUser);
    }

    @Override
    public WorkExperience updateWorkExperience(UUID userId, UUID id, WorkExperience experience) {
        WorkExperience updated = new WorkExperience(id, userId, experience.companyName(),
                experience.title(), experience.location(), experience.description(),
                experience.startDate(), experience.endDate(), experience.isCurrent(),
                experience.technologies(), experience.achievements(),
                experience.displayOrder(), null, null, experience.skills());
        return workExpRepo.save(updated);
    }

    @Override
    public List<WorkExperience> getWorkExperience(UUID userId) {
        return workExpRepo.findByUserId(userId);
    }

    @Override
    public void deleteWorkExperience(UUID userId, UUID id) {
        workExpRepo.deleteByIdAndUserId(id, userId);
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    @Override
    public Project addProject(UUID userId, Project project) {
        Project withUser = new Project(null, userId, project.name(), project.description(),
                project.technologies(), project.githubUrl(), project.liveUrl(),
                project.architectureNotes(), project.measurableOutcomes(), project.businessImpact(),
                project.startDate(), project.endDate(), project.isFeatured(),
                project.displayOrder(), null, null, project.skills());
        return projectRepo.save(withUser);
    }

    @Override
    public Project updateProject(UUID userId, UUID id, Project project) {
        Project updated = new Project(id, userId, project.name(), project.description(),
                project.technologies(), project.githubUrl(), project.liveUrl(),
                project.architectureNotes(), project.measurableOutcomes(), project.businessImpact(),
                project.startDate(), project.endDate(), project.isFeatured(),
                project.displayOrder(), null, null, project.skills());
        return projectRepo.save(updated);
    }

    @Override
    public List<Project> getProjects(UUID userId) {
        return projectRepo.findByUserId(userId);
    }

    @Override
    public void deleteProject(UUID userId, UUID id) {
        projectRepo.deleteByIdAndUserId(id, userId);
    }

    // ── Education ─────────────────────────────────────────────────────────────

    @Override
    public Education addEducation(UUID userId, Education education) {
        Education withUser = new Education(null, userId, education.institution(), education.degree(),
                education.fieldOfStudy(), education.startDate(), education.endDate(),
                education.description(), education.grade(), education.displayOrder(), null, null,
                education.skills());
        return educationRepo.save(withUser);
    }

    @Override
    public Education updateEducation(UUID userId, UUID id, Education education) {
        Education updated = new Education(id, userId, education.institution(), education.degree(),
                education.fieldOfStudy(), education.startDate(), education.endDate(),
                education.description(), education.grade(), education.displayOrder(), null, null,
                education.skills());
        return educationRepo.save(updated);
    }

    @Override
    public List<Education> getEducation(UUID userId) {
        return educationRepo.findByUserId(userId);
    }

    @Override
    public void deleteEducation(UUID userId, UUID id) {
        educationRepo.deleteByIdAndUserId(id, userId);
    }

    // ── Certifications ────────────────────────────────────────────────────────

    @Override
    public Certification addCertification(UUID userId, Certification certification) {
        Certification withUser = new Certification(null, userId, certification.name(),
                certification.issuer(), certification.issuedAt(), certification.expiresAt(),
                certification.credentialUrl(), null);
        return certRepo.save(withUser);
    }

    @Override
    public List<Certification> getCertifications(UUID userId) {
        return certRepo.findByUserId(userId);
    }

    @Override
    public void deleteCertification(UUID userId, UUID id) {
        certRepo.deleteByIdAndUserId(id, userId);
    }
}
