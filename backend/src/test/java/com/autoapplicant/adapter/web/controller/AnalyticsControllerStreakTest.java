package com.autoapplicant.adapter.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.analytics.ActivityStreak;
import com.autoapplicant.port.in.analytics.GetActivityStreakUseCase;
import com.autoapplicant.port.in.analytics.GetApplicationMetricsUseCase;
import com.autoapplicant.port.in.analytics.GetDetailedMetricsUseCase;
import com.autoapplicant.port.in.analytics.GetFunnelVelocityUseCase;
import com.autoapplicant.port.in.analytics.GetWeeklyTrendUseCase;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerStreakTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetActivityStreakUseCase getActivityStreak;
    @MockitoBean GetApplicationMetricsUseCase getMetrics;
    @MockitoBean GetDetailedMetricsUseCase getDetailedMetrics;
    @MockitoBean GetWeeklyTrendUseCase getWeeklyTrend;
    @MockitoBean GetFunnelVelocityUseCase getFunnelVelocity;
    @MockitoBean SecurityContextHelper secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase resolveLinkedInUser;
    @MockitoBean FirebaseAuth firebaseAuth;

    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void counts_in_the_requested_zone() throws Exception {
        when(getActivityStreak.getActivityStreak(userId, ZoneId.of("Europe/Copenhagen")))
                .thenReturn(new ActivityStreak(4));

        mvc.perform(get("/api/v1/analytics/streak").param("zone", "Europe/Copenhagen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(4));
    }

    @Test
    void defaults_to_utc_without_a_zone() throws Exception {
        when(getActivityStreak.getActivityStreak(userId, ZoneId.of("UTC")))
                .thenReturn(new ActivityStreak(0));

        mvc.perform(get("/api/v1/analytics/streak"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(0));
    }

    @Test
    void rejects_an_unknown_zone() throws Exception {
        mvc.perform(get("/api/v1/analytics/streak").param("zone", "Mars/Olympus_Mons"))
                .andExpect(status().isBadRequest());

        verify(getActivityStreak, never()).getActivityStreak(any(), any());
    }
}
