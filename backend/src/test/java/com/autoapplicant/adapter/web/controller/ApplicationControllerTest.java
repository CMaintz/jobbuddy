package com.autoapplicant.adapter.web.controller;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
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
                .andExpect(jsonPath("$[0].jobId").value(jobId.toString()))
                .andExpect(jsonPath("$[1].status").value("PREPARING"));
    }

    // ── POST /api/v1/applications ─────────────────────────────────────────────

    @Test
    void create_returns_201_with_saved_status() throws Exception {
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
                                {"jobId":"%s","cvVersionId":"%s","notes":"my notes","status":"preparing"}
                                """.formatted(jobId, cvId)))
                .andExpect(status().isCreated());

        CreateApplicationCommand command = capturedCreateCommand();
        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.jobId()).isEqualTo(jobId);
        assertThat(command.cvVersionId()).isEqualTo(cvId);
        assertThat(command.notes()).isEqualTo("my notes");
        assertThat(command.status()).isEqualTo(PREPARING);
    }

    @Test
    void create_with_unknown_status_falls_back_to_saved() throws Exception {
        // Lenient today: an unrecognised status is treated as absent rather than rejected.
        when(createUseCase.createApplication(any(CreateApplicationCommand.class)))
                .thenReturn(application(appId, userId, jobId, SAVED));

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s","status":"HIRED_ALREADY"}
                                """.formatted(jobId)))
                .andExpect(status().isCreated());

        assertThat(capturedCreateCommand().status()).isEqualTo(SAVED);
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

    private CreateApplicationCommand capturedCreateCommand() {
        ArgumentCaptor<CreateApplicationCommand> captor =
                ArgumentCaptor.forClass(CreateApplicationCommand.class);
        verify(createUseCase).createApplication(captor.capture());
        return captor.getValue();
    }

    private Application application(UUID id, UUID userId, UUID jobId, ApplicationStatus status) {
        return new Application(id, userId, jobId, status, null,
                null, null, null, null, null,
                null, null, null, null, null,
                Instant.now(), Instant.now(), null, null);
    }
}
