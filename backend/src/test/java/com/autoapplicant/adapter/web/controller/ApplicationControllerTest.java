package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.port.in.application.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ApplicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApplicationControllerTest {

    @Autowired MockMvc      mvc;
    @Autowired ObjectMapper mapper;

    @MockBean CreateApplicationUseCase      createUseCase;
    @MockBean UpdateApplicationStatusUseCase updateStatusUseCase;
    @MockBean GetApplicationsUseCase         getAllUseCase;
    @MockBean GetApplicationByIdUseCase      getByIdUseCase;
    @MockBean SecurityContextHelper          secCtx;

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
        when(getAllUseCase.getApplications(userId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void list_returns_all_user_applications() throws Exception {
        Application a1 = application(appId, userId, jobId, SAVED);
        Application a2 = application(UUID.randomUUID(), userId, jobId, PREPARING);
        when(getAllUseCase.getApplications(userId)).thenReturn(List.of(a1, a2));

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("SAVED"))
                .andExpect(jsonPath("$[1].status").value("PREPARING"));
    }

    @Test
    void list_response_includes_job_id() throws Exception {
        when(getAllUseCase.getApplications(userId))
                .thenReturn(List.of(application(appId, userId, jobId, SAVED)));

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(jobId.toString()));
    }

    // ── POST /api/v1/applications ─────────────────────────────────────────────

    @Test
    void create_returns_200_with_saved_status() throws Exception {
        Application saved = application(appId, userId, jobId, SAVED);
        when(createUseCase.createApplication(eq(userId), eq(jobId), isNull(), isNull()))
                .thenReturn(saved);

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s"}
                                """.formatted(jobId)))
                .andExpect(status().isOk())
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
        when(createUseCase.createApplication(eq(userId), eq(jobId), eq(cvId), eq("my notes")))
                .thenReturn(application(appId, userId, jobId, SAVED));

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s","cvVersionId":"%s","notes":"my notes"}
                                """.formatted(jobId, cvId)))
                .andExpect(status().isOk());

        verify(createUseCase).createApplication(userId, jobId, cvId, "my notes");
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

    private Application application(UUID id, UUID userId, UUID jobId, ApplicationStatus status) {
        return new Application(id, userId, jobId, status, null,
                null, null, null, null, null,
                null, null, null, null,
                Instant.now(), Instant.now());
    }
}
