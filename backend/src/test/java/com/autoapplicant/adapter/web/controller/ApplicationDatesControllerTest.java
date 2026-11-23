package com.autoapplicant.adapter.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.port.in.application.UpdateApplicationDatesUseCase;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

@WebMvcTest(controllers = ApplicationDatesController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApplicationDatesControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UpdateApplicationDatesUseCase updateDates;
    @MockitoBean GetJobByIdUseCase getJobById;
    @MockitoBean JobRepositoryPort jobRepo;
    @MockitoBean SecurityContextHelper secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase resolveLinkedInUser;
    @MockitoBean FirebaseAuth firebaseAuth;

    UUID userId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
        when(getJobById.getJobById(any())).thenReturn(Optional.empty());
    }

    private Application withDates(LocalDate deadline, LocalDateTime interviewAt) {
        return new Application(appId, userId, UUID.randomUUID(), ApplicationStatus.INTERVIEW, null,
                null, null, null, null, null, null, null, null, null, null,
                Instant.now(), Instant.now(), null, null, deadline, interviewAt);
    }

    @Test
    void anInterviewTimeIsKeptAsTheWallClockTimeTheUserTyped() throws Exception {
        LocalDateTime nineOClock = LocalDateTime.of(2026, 11, 20, 9, 0);
        when(updateDates.updateDates(appId, userId, LocalDate.of(2026, 11, 18), nineOClock))
                .thenReturn(withDates(LocalDate.of(2026, 11, 18), nineOClock));

        mvc.perform(put("/api/v1/applications/{id}/dates", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deadline":"2026-11-18","interviewAt":"2026-11-20T09:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadline").value("2026-11-18"))
                .andExpect(jsonPath("$.interviewAt").value("2026-11-20T09:00:00"));
    }
}
