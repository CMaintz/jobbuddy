package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobContact;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.document.GetDocumentsForJobUseCase;
import com.autoapplicant.port.in.job.*;
import com.autoapplicant.port.in.matching.SubmitRecommendationFeedbackUseCase;
import com.google.firebase.auth.FirebaseAuth;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The job payload's field names, which the frontend's Job model reads, and the page-size cap.
 */
@WebMvcTest(controllers = JobController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobControllerContractTest {

    private static final String TOO_MANY = "5000";

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
    @MockitoBean SecurityContextHelper               secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase        provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase          resolveLinkedInUser;
    @MockitoBean FirebaseAuth                        firebaseAuth;

    UUID userId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void a_single_job_uses_the_frontend_field_names() throws Exception {
        when(getJobById.getJobById(jobId)).thenReturn(Optional.of(closedJobWithContact()));

        mvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.active").doesNotExist())
                .andExpect(jsonPath("$.duplicateGroupId").value(groupId.toString()))
                .andExpect(jsonPath("$.contact.name").value("Mette Hansen"))
                .andExpect(jsonPath("$.contact.phone").value("12 34 56 78"));
    }

    @Test
    void saved_jobs_carry_the_inactive_flag() throws Exception {
        when(getSavedJobs.getSavedJobs(userId)).thenReturn(List.of(closedJobWithContact()));

        mvc.perform(get("/api/v1/jobs/saved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isActive").value(false))
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andExpect(jsonPath("$[0].contact.name").value("Mette Hansen"));
    }

    @Test
    void list_clamps_an_oversized_page_and_a_zero_one() throws Exception {
        when(getJobs.getJobs(any(JobSearchQuery.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/jobs").param("size", TOO_MANY)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/jobs").param("size", "0")).andExpect(status().isOk());

        ArgumentCaptor<JobSearchQuery> captor = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(getJobs, times(2)).getJobs(captor.capture());
        assertThat(captor.getAllValues()).extracting(JobSearchQuery::size)
                .containsExactly(PageLimits.MAX_PAGE_SIZE, 1);
    }

    @Test
    void search_clamps_an_oversized_page() throws Exception {
        when(searchJobs.searchJobs(any(JobSearchQuery.class)))
                .thenReturn(new JobSearchResult(List.of(), 0, 0, PageLimits.MAX_PAGE_SIZE, null));

        mvc.perform(get("/api/v1/jobs/search").param("size", TOO_MANY))
                .andExpect(status().isOk());

        ArgumentCaptor<JobSearchQuery> captor = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(searchJobs).searchJobs(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(PageLimits.MAX_PAGE_SIZE);
    }

    @Test
    void recommendations_clamp_an_oversized_limit() throws Exception {
        when(getRecommendations.getRecommendations(eq(userId), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/v1/jobs/recommendations").param("limit", TOO_MANY))
                .andExpect(status().isOk());

        verify(getRecommendations).getRecommendations(userId, PageLimits.MAX_PAGE_SIZE);
    }

    private Job closedJobWithContact() {
        return Job.builder()
                .id(jobId).source(JobSource.MANUAL).title("Dev").companyName("Acme")
                .isActive(false).duplicateGroupId(groupId)
                .contact(new JobContact("Mette Hansen", "afdelingsleder", null, "12 34 56 78"))
                .build();
    }
}
