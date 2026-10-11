package com.autoapplicant.adapter.web.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyResearch;
import com.autoapplicant.domain.company.OutreachContact;
import com.autoapplicant.domain.company.OutreachReason;
import com.autoapplicant.domain.company.OutreachStatus;
import com.autoapplicant.domain.company.OutreachTarget;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.company.FindOutreachTargetsUseCase;
import com.autoapplicant.port.in.company.GetCompaniesUseCase;
import com.autoapplicant.port.in.company.ManageCompanyResearchUseCase;
import com.autoapplicant.port.in.company.ManageOutreachUseCase;
import com.autoapplicant.port.in.job.GetJobsByCompanyUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(controllers = CompanyController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompanyControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetCompaniesUseCase          companies;
    @MockitoBean ManageCompanyResearchUseCase research;
    @MockitoBean FindOutreachTargetsUseCase   outreachTargets;
    @MockitoBean ManageOutreachUseCase        outreach;
    @MockitoBean GetJobsByCompanyUseCase      companyJobs;
    @MockitoBean SecurityContextHelper        secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase   resolveLinkedInUser;
    @MockitoBean FirebaseAuth                 firebaseAuth;

    UUID userId    = UUID.randomUUID();
    UUID companyId = UUID.randomUUID();
    UUID outreachId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void search_passes_query_and_paging() throws Exception {
        when(companies.searchCompanies("acme", 1, 10)).thenReturn(List.of(company()));

        mvc.perform(get("/api/v1/companies").param("q", "acme").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Acme"));

        verify(companies).searchCompanies("acme", 1, 10);
    }

    @Test
    void search_clamps_an_oversized_page() throws Exception {
        when(companies.searchCompanies("acme", 0, PageLimits.MAX_PAGE_SIZE)).thenReturn(List.of());

        mvc.perform(get("/api/v1/companies").param("q", "acme").param("size", "5000"))
                .andExpect(status().isOk());

        verify(companies).searchCompanies("acme", 0, PageLimits.MAX_PAGE_SIZE);
    }

    @Test
    void get_by_id_returns_company() throws Exception {
        when(companies.getCompanyById(companyId)).thenReturn(Optional.of(company()));

        mvc.perform(get("/api/v1/companies/{id}", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId.toString()))
                .andExpect(jsonPath("$.slug").value("acme"));
    }

    @Test
    void get_by_id_returns_404_when_missing() throws Exception {
        when(companies.getCompanyById(companyId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/companies/{id}", companyId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_research_returns_empty_notes_when_none_saved() throws Exception {
        when(research.getResearch(userId, companyId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/companies/{id}/research", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value(nullValue()));
    }

    @Test
    void save_research_returns_saved_notes() throws Exception {
        when(research.saveResearch(userId, companyId, "They ship weekly"))
                .thenReturn(Optional.of(new CompanyResearch("They ship weekly", Instant.now())));

        mvc.perform(put("/api/v1/companies/{id}/research", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes":"They ship weekly"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("They ship weekly"));
    }

    @Test
    void save_research_returns_404_for_unknown_company() throws Exception {
        when(research.saveResearch(eq(userId), eq(companyId), any())).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/companies/{id}/research", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes":"x"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void outreach_targets_pass_limit_and_hiring_flag() throws Exception {
        OutreachTarget target = new OutreachTarget(companyId, "Acme", null, 72,
                List.<OutreachReason>of(), null, List.of("Java"), false);
        when(outreachTargets.findOutreachTargets(userId, 7, true)).thenReturn(List.of(target));

        mvc.perform(get("/api/v1/companies/outreach-targets")
                        .param("limit", "7").param("includeHiringNow", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("Acme"))
                .andExpect(jsonPath("$[0].score").value(72));

        verify(outreachTargets).findOutreachTargets(userId, 7, true);
    }

    @Test
    void track_outreach_passes_request_fields() throws Exception {
        when(outreach.track(userId, companyId, "Acme", "Jane"))
                .thenReturn(outreach(OutreachStatus.SAVED, null));

        mvc.perform(post("/api/v1/companies/outreach")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","companyName":"Acme","contactName":"Jane"}
                                """.formatted(companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(outreachId.toString()))
                .andExpect(jsonPath("$.status").value("SAVED"));

        verify(outreach).track(userId, companyId, "Acme", "Jane");
    }

    @Test
    void update_outreach_parses_status_and_follow_up_date() throws Exception {
        LocalDate due = LocalDate.of(2026, 10, 15);
        when(outreach.update(userId, outreachId, OutreachStatus.CONTACTED, "email", due, null))
                .thenReturn(outreach(OutreachStatus.CONTACTED, due));

        mvc.perform(patch("/api/v1/companies/outreach/{id}", outreachId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONTACTED","channel":"email","followUpDue":"2026-10-15"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.followUpDue").value("2026-10-15"));

        verify(outreach).update(userId, outreachId, OutreachStatus.CONTACTED, "email", due, null);
    }

    @Test
    void update_outreach_maps_not_found_to_400() throws Exception {
        when(outreach.update(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Outreach not found"));

        mvc.perform(patch("/api/v1/companies/outreach/{id}", outreachId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Outreach not found"));
    }

    @Test
    void untrack_outreach_returns_204() throws Exception {
        mvc.perform(delete("/api/v1/companies/outreach/{id}", outreachId))
                .andExpect(status().isNoContent());

        verify(outreach).untrack(userId, outreachId);
    }

    private Company company() {
        return new Company(companyId, "Acme", "acme", "https://acme.example", null, null, null,
                null, "Software", "DK", false, false, Instant.now(), Instant.now());
    }

    private OutreachContact outreach(OutreachStatus status, LocalDate followUpDue) {
        return new OutreachContact(outreachId, userId, companyId, "Acme", status, null, "Jane",
                null, followUpDue, null, Instant.now(), Instant.now());
    }
}
