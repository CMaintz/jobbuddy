package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.Certification;
import com.autoapplicant.domain.user.Education;
import com.autoapplicant.domain.user.Project;
import com.autoapplicant.domain.user.WorkExperience;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.in.user.ManageCustomSectionsUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProfileSectionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileSectionControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageWorkExperienceUseCase  workExpUseCase;
    @MockitoBean ManageProjectsUseCase        projectsUseCase;
    @MockitoBean ManageEducationUseCase       educationUseCase;
    @MockitoBean ManageCertificationsUseCase  certUseCase;
    @MockitoBean GetUserProfileUseCase        profileUseCase;
    @MockitoBean ManageProfileSocialUseCase   socialUseCase;
    @MockitoBean ManageProfileStrengthUseCase strengthUseCase;
    @MockitoBean ManageSpokenLanguagesUseCase languageUseCase;
    @MockitoBean ManageCustomSectionsUseCase  customSections;
    @MockitoBean SecurityContextHelper        secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase   resolveLinkedInUser;
    @MockitoBean FirebaseAuth                 firebaseAuth;

    UUID userId  = UUID.randomUUID();
    UUID entryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void full_profile_aggregates_sections_with_null_profile_when_missing() throws Exception {
        when(profileUseCase.getProfile(userId)).thenReturn(Optional.empty());
        when(workExpUseCase.getWorkExperience(userId)).thenReturn(List.of(experience(entryId)));
        when(projectsUseCase.getProjects(userId)).thenReturn(List.of(project(UUID.randomUUID())));

        mvc.perform(get("/api/v1/profile/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value(nullValue()))
                .andExpect(jsonPath("$.experience", hasSize(1)))
                .andExpect(jsonPath("$.experience[0].companyName").value("Acme"))
                .andExpect(jsonPath("$.projects[0].name").value("JobBuddy"))
                .andExpect(jsonPath("$.education", hasSize(0)))
                .andExpect(jsonPath("$.strengths", hasSize(0)));
    }

    @Test
    void list_experience_returns_current_users_entries() throws Exception {
        when(workExpUseCase.getWorkExperience(userId)).thenReturn(List.of(experience(entryId)));

        mvc.perform(get("/api/v1/profile/experience"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(entryId.toString()))
                .andExpect(jsonPath("$[0].startDate").value("2022-01-01"));
    }

    @Test
    void add_experience_returns_201_with_location() throws Exception {
        when(workExpUseCase.addWorkExperience(eq(userId), any(WorkExperience.class)))
                .thenReturn(experience(entryId));

        mvc.perform(post("/api/v1/profile/experience")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Acme","title":"Engineer","startDate":"2022-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/profile/experience/" + entryId))
                .andExpect(jsonPath("$.title").value("Engineer"));

        ArgumentCaptor<WorkExperience> captor = ArgumentCaptor.forClass(WorkExperience.class);
        verify(workExpUseCase).addWorkExperience(eq(userId), captor.capture());
        assertThat(captor.getValue().companyName()).isEqualTo("Acme");
        assertThat(captor.getValue().startDate()).isEqualTo(LocalDate.of(2022, 1, 1));
    }

    @Test
    void update_experience_passes_path_id_and_user() throws Exception {
        when(workExpUseCase.updateWorkExperience(eq(userId), eq(entryId), any(WorkExperience.class)))
                .thenReturn(experience(entryId));

        mvc.perform(put("/api/v1/profile/experience/{id}", entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Acme","title":"Engineer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId.toString()));

        verify(workExpUseCase).updateWorkExperience(eq(userId), eq(entryId), any(WorkExperience.class));
    }

    @Test
    void add_project_returns_201_with_location() throws Exception {
        when(projectsUseCase.addProject(eq(userId), any(Project.class))).thenReturn(project(entryId));

        mvc.perform(post("/api/v1/profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"JobBuddy","technologies":["Java","Angular"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/profile/projects/" + entryId));

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectsUseCase).addProject(eq(userId), captor.capture());
        assertThat(captor.getValue().technologies()).containsExactly("Java", "Angular");
    }

    @Test
    void add_education_returns_201_with_location() throws Exception {
        Education saved = new Education(entryId, userId, "DTU", "MSc", "CS", null, null, null,
                null, 0, Instant.now(), Instant.now(), List.of());
        when(educationUseCase.addEducation(eq(userId), any(Education.class))).thenReturn(saved);

        mvc.perform(post("/api/v1/profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"institution":"DTU","degree":"MSc"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/profile/education/" + entryId))
                .andExpect(jsonPath("$.institution").value("DTU"));
    }

    @Test
    void add_certification_returns_201_with_location() throws Exception {
        Certification saved = new Certification(entryId, userId, "AWS SAA", "Amazon",
                LocalDate.of(2024, 5, 1), null, null, Instant.now());
        when(certUseCase.addCertification(eq(userId), any(Certification.class))).thenReturn(saved);

        mvc.perform(post("/api/v1/profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"AWS SAA","issuer":"Amazon","issuedAt":"2024-05-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/profile/certifications/" + entryId))
                .andExpect(jsonPath("$.issuedAt").value("2024-05-01"));
    }

    @Test
    void deletes_return_204_and_pass_user_then_id() throws Exception {
        mvc.perform(delete("/api/v1/profile/experience/{id}", entryId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/profile/projects/{id}", entryId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/profile/education/{id}", entryId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/profile/certifications/{id}", entryId))
                .andExpect(status().isNoContent());

        verify(workExpUseCase).deleteWorkExperience(userId, entryId);
        verify(projectsUseCase).deleteProject(userId, entryId);
        verify(educationUseCase).deleteEducation(userId, entryId);
        verify(certUseCase).deleteCertification(userId, entryId);
    }

    private WorkExperience experience(UUID id) {
        return new WorkExperience(id, userId, "Acme", "Engineer", "Copenhagen", null,
                LocalDate.of(2022, 1, 1), null, true, List.of("Java"), List.of(), 0,
                Instant.now(), Instant.now(), List.of());
    }

    private Project project(UUID id) {
        return new Project(id, userId, "JobBuddy", null, List.of("Java", "Angular"), null, null,
                null, null, null, null, null, false, 0, Instant.now(), Instant.now(), List.of());
    }
}
