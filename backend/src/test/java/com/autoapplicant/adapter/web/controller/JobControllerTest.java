package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.job.EmploymentType;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.document.GetDocumentsForJobUseCase;
import com.autoapplicant.port.in.job.*;
import com.autoapplicant.port.in.matching.SubmitRecommendationFeedbackUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = JobController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobControllerTest {

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
    UUID jobId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void list_builds_query_for_current_user_and_returns_page() throws Exception {
        when(getJobs.getJobs(any(JobSearchQuery.class)))
                .thenReturn(new PageImpl<>(List.of(job(jobId, "Backend Dev", "short"))));

        mvc.perform(get("/api/v1/jobs").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Backend Dev"));

        ArgumentCaptor<JobSearchQuery> captor = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(getJobs).getJobs(captor.capture());
        JobSearchQuery query = captor.getValue();
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(5);
        assertThat(query.sortBy()).isEqualTo("postedAt");
        assertThat(query.userId()).isEqualTo(userId);
    }

    @Test
    void list_truncates_long_descriptions_in_preview() throws Exception {
        String longText = "x".repeat(2000);
        when(getJobs.getJobs(any(JobSearchQuery.class)))
                .thenReturn(new PageImpl<>(List.of(job(jobId, "Dev", longText))));

        mvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].descriptionTruncated").value(true));
    }

    @Test
    void get_by_id_returns_full_job() throws Exception {
        String longText = "y".repeat(2000);
        when(getJobById.getJobById(jobId)).thenReturn(Optional.of(job(jobId, "Dev", longText)));

        mvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.descriptionClean").value(longText))
                .andExpect(jsonPath("$.descriptionTruncated").value(false));
    }

    @Test
    void get_by_id_returns_404_when_missing() throws Exception {
        when(getJobById.getJobById(jobId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookup_returns_404_when_url_unknown() throws Exception {
        when(getJobById.lookupByUrl("https://example.com/x")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/jobs/lookup").param("url", "https://example.com/x"))
                .andExpect(status().isNotFound());
    }

    @Test
    void recommendations_map_match_results() throws Exception {
        MatchResult result = new MatchResult(jobId, userId, job(jobId, "Dev", "d"), true,
                0.8, 0.5, 87, MatchLabel.STRONG, List.of("Java"));
        when(getRecommendations.getRecommendations(userId, 3)).thenReturn(List.of(result));

        mvc.perform(get("/api/v1/jobs/recommendations").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(jobId.toString()))
                .andExpect(jsonPath("$[0].totalScore").value(87))
                .andExpect(jsonPath("$[0].matchLabel").value("STRONG"))
                .andExpect(jsonPath("$[0].job.title").value("Dev"));
    }

    @Test
    void save_and_ignore_pass_current_user_and_job() throws Exception {
        mvc.perform(post("/api/v1/jobs/{id}/save", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        mvc.perform(post("/api/v1/jobs/{id}/ignore", jobId).param("reason", "too far"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/jobs/{id}/report-inactive", jobId))
                .andExpect(status().isAccepted());

        verify(saveJob).saveJob(userId, jobId);
        verify(ignoreJob).ignoreJob(userId, jobId, "too far");
        verify(reportJobInactive).reportInactive(userId, jobId);
    }

    @Test
    void feedback_passes_feedback_type() throws Exception {
        when(feedbackUseCase.submitFeedback(userId, jobId, FeedbackType.LIKE))
                .thenReturn(new RecommendationFeedback(UUID.randomUUID(), userId, jobId,
                        FeedbackType.LIKE, Instant.now()));

        mvc.perform(post("/api/v1/jobs/{id}/feedback", jobId).param("type", "LIKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackType").value("LIKE"));
    }

    @Test
    void semantic_search_and_similar_clamp_limit() throws Exception {
        when(semanticSearch.semanticSearch("java", 100)).thenReturn(List.of());
        when(getSimilarJobs.getSimilarJobs(jobId, 1)).thenReturn(List.of());

        mvc.perform(get("/api/v1/jobs/search/semantic").param("q", "java").param("limit", "500"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/jobs/{id}/similar", jobId).param("limit", "0"))
                .andExpect(status().isOk());

        verify(semanticSearch).semanticSearch("java", 100);
        verify(getSimilarJobs).getSimilarJobs(jobId, 1);
    }

    @Test
    void add_manually_creates_manual_job_with_defaults() throws Exception {
        when(createManualJob.createManualJob(any(Job.class))).thenReturn(job(jobId, "Dev", "d"));

        mvc.perform(post("/api/v1/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dev","companyName":"Acme","remoteType":"HYBRID",
                                 "employmentType":"FULL_TIME"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/jobs/" + jobId))
                .andExpect(jsonPath("$.id").value(jobId.toString()));

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(createManualJob).createManualJob(captor.capture());
        Job sent = captor.getValue();
        assertThat(sent.source()).isEqualTo(JobSource.MANUAL);
        assertThat(sent.title()).isEqualTo("Dev");
        assertThat(sent.currency()).isEqualTo("DKK");
        assertThat(sent.remoteType()).isEqualTo(RemoteType.HYBRID);
        assertThat(sent.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(sent.isActive()).isTrue();
    }

    @Test
    void add_manually_returns_400_when_title_blank() throws Exception {
        mvc.perform(post("/api/v1/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("title")));

        verifyNoInteractions(createManualJob);
    }

    @Test
    void add_manually_returns_400_for_unknown_employment_type() throws Exception {
        mvc.perform(post("/api/v1/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dev","employmentType":"GIG"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createManualJob);
    }

    private Job job(UUID id, String title, String description) {
        return Job.builder()
                .id(id).source(JobSource.MANUAL).title(title).companyName("Acme")
                .descriptionClean(description).postedAt(Instant.now())
                .build();
    }
}
