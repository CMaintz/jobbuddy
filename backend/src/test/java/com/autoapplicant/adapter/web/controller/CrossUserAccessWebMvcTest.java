package com.autoapplicant.adapter.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.domain.user.WorkExperience;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.document.GetDocumentsForJobUseCase;
import com.autoapplicant.port.in.document.ManagePdfTemplatesUseCase;
import com.autoapplicant.port.in.job.*;
import com.autoapplicant.port.in.matching.SubmitRecommendationFeedbackUseCase;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.in.user.ManageCustomSectionsUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bob is logged in and knows the ids of Alice's rows. Each endpoint has to hand Bob's own id to
 * the use case, and a row he does not own has to come back as 404.
 */
@WebMvcTest(controllers = {JobController.class, ProfileSectionController.class, PdfTemplateController.class})
@AutoConfigureMockMvc(addFilters = false)
class CrossUserAccessWebMvcTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetJobsUseCase                      getJobs;
    @MockitoBean GetJobByIdUseCase                   getJobById;
    @MockitoBean GetSavedJobsUseCase                 getSavedJobs;
    @MockitoBean GetIgnoredJobsUseCase               getIgnoredJobs;
    @MockitoBean SearchJobsUseCase                   searchJobs;
    @MockitoBean GetRecommendationsUseCase           getRecommendations;
    @MockitoBean SaveJobUseCase                      saveJob;
    @MockitoBean IgnoreJobUseCase                    ignoreJob;
    @MockitoBean CreateManualJobUseCase              createManualJob;
    @MockitoBean SubmitRecommendationFeedbackUseCase feedbackUseCase;
    @MockitoBean GetDocumentsForJobUseCase           getDocsForJob;
    @MockitoBean ReportJobInactiveUseCase            reportJobInactive;
    @MockitoBean GetSimilarJobsUseCase               getSimilarJobs;
    @MockitoBean SemanticSearchJobsUseCase           semanticSearch;

    @MockitoBean ManageWorkExperienceUseCase  workExpUseCase;
    @MockitoBean ManageProjectsUseCase        projectsUseCase;
    @MockitoBean ManageEducationUseCase       educationUseCase;
    @MockitoBean ManageCertificationsUseCase  certUseCase;
    @MockitoBean GetUserProfileUseCase        profileUseCase;
    @MockitoBean ManageProfileSocialUseCase   socialUseCase;
    @MockitoBean ManageProfileStrengthUseCase strengthUseCase;
    @MockitoBean ManageSpokenLanguagesUseCase languageUseCase;

    @MockitoBean ManagePdfTemplatesUseCase    pdfTemplates;

    @MockitoBean ManageCustomSectionsUseCase  customSections;
    @MockitoBean SecurityContextHelper        secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase   resolveLinkedInUser;
    @MockitoBean FirebaseAuth                 firebaseAuth;

    UUID bob = UUID.randomUUID();
    UUID alicesRow = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(bob);
    }

    @Test
    void job_documents_are_fetched_for_the_current_user_only() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(getDocsForJob.getDocumentsForJob(jobId, bob)).thenReturn(List.of());

        mvc.perform(get("/api/v1/jobs/" + jobId + "/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        verify(getDocsForJob).getDocumentsForJob(jobId, bob);
    }

    @Test
    void updating_someone_elses_work_experience_is_404() throws Exception {
        when(workExpUseCase.updateWorkExperience(eq(bob), eq(alicesRow), any(WorkExperience.class)))
                .thenThrow(new NotFoundException("Work experience not found"));

        mvc.perform(put("/api/v1/profile/experience/" + alicesRow)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Hijacked\",\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updating_someone_elses_project_is_404() throws Exception {
        when(projectsUseCase.updateProject(eq(bob), eq(alicesRow), any()))
                .thenThrow(new NotFoundException("Project not found"));

        mvc.perform(put("/api/v1/profile/projects/" + alicesRow)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updating_someone_elses_education_is_404() throws Exception {
        when(educationUseCase.updateEducation(eq(bob), eq(alicesRow), any()))
                .thenThrow(new NotFoundException("Education not found"));

        mvc.perform(put("/api/v1/profile/education/" + alicesRow)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"institution\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void someone_elses_pdf_template_is_404() throws Exception {
        when(pdfTemplates.getById(alicesRow, bob)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/pdf-templates/" + alicesRow)).andExpect(status().isNotFound());
        verify(pdfTemplates).getById(alicesRow, bob);
    }
}
