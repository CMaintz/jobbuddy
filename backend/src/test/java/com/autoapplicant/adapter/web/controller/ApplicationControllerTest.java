package com.autoapplicant.adapter.web.controller;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.application.*;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ApplicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApplicationControllerTest {

    @Autowired MockMvc      mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean CreateApplicationUseCase        createUseCase;
    @MockitoBean UpdateApplicationStatusUseCase  updateStatusUseCase;
    @MockitoBean UpdateRecruiterInfoUseCase      updateRecruiterUseCase;
    @MockitoBean UpdateOutcomeUseCase            updateOutcomeUseCase;
    @MockitoBean GetApplicationsUseCase          getAllUseCase;
    @MockitoBean GetApplicationByIdUseCase       getByIdUseCase;
    @MockitoBean GetApplicationTimelineUseCase   timelineUseCase;
    @MockitoBean GetJobByIdUseCase               getJobByIdUseCase;
    @MockitoBean JobRepositoryPort               jobRepo;
    @MockitoBean SecurityContextHelper           secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase    provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase      resolveLinkedInUser;
    @MockitoBean FirebaseAuth                    firebaseAuth;

    UUID userId = UUID.randomUUID();
    UUID jobId  = UUID.randomUUID();
    UUID appId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    // ── GET /api/v1/applications ──────────────────────────────────────────────

    @Test
    void list_returns_empty_array_when_no_applications() throws Exception {
        when(getAllUseCase.getApplications(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void list_returns_all_user_applications() throws Exception {
        Application a1 = application(appId, userId, jobId, SAVED);
        Application a2 = application(UUID.randomUUID(), userId, jobId, PREPARING);
        when(getAllUseCase.getApplications(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a1, a2)));

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("SAVED"))
                .andExpect(jsonPath("$[1].status").value("PREPARING"));
    }

    @Test
    void list_response_includes_job_id() throws Exception {
        when(getAllUseCase.getApplications(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(application(appId, userId, jobId, SAVED))));

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(jobId.toString()));
    }

    // ── POST /api/v1/applications ─────────────────────────────────────────────

    @Test
    void create_returns_200_with_saved_status() throws Exception {
        Application saved = application(appId, userId, jobId, SAVED);
        when(createUseCase.createApplication(any(CreateApplicationCommand.class)))
                .thenReturn(saved);

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s"}
                                """.formatted(jobId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(appId.toString()))
                .andExpect(jsonPath("$.status").value("SAVED"));
    }

    @Test
    void create_returns_400_when_job_id_missing() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createUseCase);
    }

    @Test
    void create_passes_optional_cv_version_id_and_notes() throws Exception {
        UUID cvId = UUID.randomUUID();
        when(createUseCase.createApplication(any(CreateApplicationCommand.class)))
                .thenReturn(application(appId, userId, jobId, SAVED));

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s","cvVersionId":"%s","notes":"my notes"}
                                """.formatted(jobId, cvId)))
                .andExpect(status().isCreated());

        verify(createUseCase).createApplication(any(CreateApplicationCommand.class));
    }

    // ── GET /api/v1/applications/{id} ─────────────────────────────────────────

    @Test
    void get_one_returns_application_when_found() throws Exception {
        Application app = application(appId, userId, jobId, APPLIED);
        when(getByIdUseCase.getApplicationById(appId, userId)).thenReturn(Optional.of(app));

        mvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appId.toString()))
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void get_one_returns_404_when_not_found() throws Exception {
        when(getByIdUseCase.getApplicationById(appId, userId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/v1/applications/{id}/status ────────────────────────────────

    @Test
    void update_status_returns_updated_application() throws Exception {
        Application updated = application(appId, userId, jobId, PREPARING);
        when(updateStatusUseCase.updateStatus(appId, userId, PREPARING, null)).thenReturn(updated);

        mvc.perform(patch("/api/v1/applications/{id}/status", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PREPARING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    @Test
    void update_status_returns_400_for_missing_status() throws Exception {
        mvc.perform(patch("/api/v1/applications/{id}/status", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_status_returns_409_on_invalid_transition() throws Exception {
        when(updateStatusUseCase.updateStatus(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Invalid status transition"));

        mvc.perform(patch("/api/v1/applications/{id}/status", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OFFER"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void update_status_keeps_the_job_on_the_row_and_lists_the_next_moves() throws Exception {
        when(updateStatusUseCase.updateStatus(eq(appId), eq(userId), eq(APPLIED), any()))
                .thenReturn(application(appId, userId, jobId, APPLIED));
        when(getJobByIdUseCase.getJobById(jobId)).thenReturn(Optional.of(
                Job.builder().id(jobId).title("Kommunikationsmedarbejder").companyName("Acme A/S").build()));

        mvc.perform(patch("/api/v1/applications/{id}/status", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPLIED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.jobTitle").value("Kommunikationsmedarbejder"))
                .andExpect(jsonPath("$.jobCompanyName").value("Acme A/S"))
                .andExpect(jsonPath("$.nextStatuses", containsInAnyOrder("RECRUITER_CONTACT", "REJECTED", "ARCHIVED")));
    }

    @Test
    void a_saved_application_offers_applied_as_a_next_move() throws Exception {
        when(getAllUseCase.getApplications(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(application(appId, userId, jobId, SAVED))));

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nextStatuses", containsInAnyOrder("PREPARING", "APPLIED", "ARCHIVED")));
    }

    @Test
    void update_status_returns_400_on_not_found() throws Exception {
        when(updateStatusUseCase.updateStatus(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Application not found"));

        mvc.perform(patch("/api/v1/applications/{id}/status", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PREPARING"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Application application(UUID id, UUID userId, UUID jobId, ApplicationStatus status) {
        return new Application(id, userId, jobId, status, null,
                null, null, null, null, null,
                null, null, null, null, null,
                Instant.now(), Instant.now(), null, null, null, null);
    }
}
