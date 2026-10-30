package com.autoapplicant.adapter.web.controller;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationTimelineEntry;
import com.autoapplicant.port.in.application.*;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The per-application sub-resources: attach, recruiter, outcome and timeline. */
@WebMvcTest(controllers = ApplicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApplicationControllerSubResourceTest {

    @Autowired MockMvc mvc;

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
    UUID appId  = UUID.randomUUID();
    UUID docId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void attach_passes_body_fields_and_parses_status_case_insensitively() throws Exception {
        when(createUseCase.attachGeneratedDocument(any(), any(), any(), any(), any(), any()))
                .thenReturn(application(APPLIED));

        mvc.perform(post("/api/v1/applications/{id}/generated-documents/{documentId}", appId, docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"generatedContent":"Dear Mette","status":"applied","notes":"sent"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        verify(createUseCase).attachGeneratedDocument(appId, userId, docId, "Dear Mette", APPLIED, "sent");
    }

    @Test
    void attach_with_unknown_status_leaves_status_unchanged() throws Exception {
        // Unlike create, the fallback here is null: "do not move the application".
        when(createUseCase.attachGeneratedDocument(any(), any(), any(), any(), any(), any()))
                .thenReturn(application(PREPARING));

        mvc.perform(post("/api/v1/applications/{id}/generated-documents/{documentId}", appId, docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"NOT_A_STATUS"}
                                """))
                .andExpect(status().isOk());

        verify(createUseCase).attachGeneratedDocument(
                eq(appId), eq(userId), eq(docId), isNull(), isNull(), isNull());
    }

    @Test
    void attach_without_a_body_passes_nulls() throws Exception {
        when(createUseCase.attachGeneratedDocument(any(), any(), any(), any(), any(), any()))
                .thenReturn(application(PREPARING));

        mvc.perform(post("/api/v1/applications/{id}/generated-documents/{documentId}", appId, docId))
                .andExpect(status().isOk());

        verify(createUseCase).attachGeneratedDocument(
                eq(appId), eq(userId), eq(docId), isNull(), isNull(), isNull());
    }

    @Test
    void recruiter_update_maps_each_field_to_its_own_argument() throws Exception {
        when(updateRecruiterUseCase.updateRecruiterInfo(any(), any(), any(), any(), any(), any()))
                .thenReturn(application(RECRUITER_CONTACT));

        mvc.perform(patch("/api/v1/applications/{id}/recruiter", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recruiterName":"Mette","recruiterEmail":"mh@example.dk",
                                 "recruiterMessage":"Hej","recruiterReply":"Tak"}
                                """))
                .andExpect(status().isOk());

        verify(updateRecruiterUseCase).updateRecruiterInfo(
                appId, userId, "Mette", "mh@example.dk", "Hej", "Tak");
    }

    @Test
    void outcome_update_maps_feedback_and_lessons() throws Exception {
        when(updateOutcomeUseCase.updateOutcome(any(), any(), any(), any()))
                .thenReturn(application(REJECTED));

        mvc.perform(patch("/api/v1/applications/{id}/outcome", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcomeFeedback":"Too junior","outcomeLessons":"Lead with scale"}
                                """))
                .andExpect(status().isOk());

        verify(updateOutcomeUseCase).updateOutcome(appId, userId, "Too junior", "Lead with scale");
    }

    @Test
    void timeline_is_scoped_to_the_current_user() throws Exception {
        when(timelineUseCase.getTimeline(appId, userId)).thenReturn(List.of(
                new ApplicationTimelineEntry(Instant.parse("2026-01-01T00:00:00Z"),
                        APPLIED, RECRUITER_CONTACT, true, "They called")));

        mvc.perform(get("/api/v1/applications/{id}/timeline", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$[0].toStatus").value("RECRUITER_CONTACT"))
                .andExpect(jsonPath("$[0].employerResponse").value(true))
                .andExpect(jsonPath("$[0].notes").value("They called"));
    }

    private Application application(ApplicationStatus status) {
        return new Application(appId, userId, UUID.randomUUID(), status, null,
                null, null, null, null, null,
                null, null, null, null, null,
                Instant.now(), Instant.now(), null, null);
    }
}
