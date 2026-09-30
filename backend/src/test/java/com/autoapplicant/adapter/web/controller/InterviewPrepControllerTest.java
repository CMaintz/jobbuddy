package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.interview.InterviewPrepPack;
import com.autoapplicant.domain.interview.InterviewQuestion;
import com.autoapplicant.domain.interview.MockInterviewTurn;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.interview.GenerateInterviewPrepUseCase;
import com.autoapplicant.port.in.interview.GenerateInterviewQuestionsUseCase;
import com.autoapplicant.port.in.interview.ManageInterviewQuestionsUseCase;
import com.autoapplicant.port.in.interview.MockInterviewUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InterviewPrepController.class)
@AutoConfigureMockMvc(addFilters = false)
class InterviewPrepControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageInterviewQuestionsUseCase   manage;
    @MockitoBean GenerateInterviewQuestionsUseCase generate;
    @MockitoBean GenerateInterviewPrepUseCase      generatePrep;
    @MockitoBean MockInterviewUseCase              mockInterview;
    @MockitoBean SecurityContextHelper             secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase      provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase        resolveLinkedInUser;
    @MockitoBean FirebaseAuth                      firebaseAuth;

    UUID userId     = UUID.randomUUID();
    UUID jobId      = UUID.randomUUID();
    UUID questionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void list_returns_questions_for_job_and_user() throws Exception {
        when(manage.getQuestions(jobId, userId)).thenReturn(List.of(question(questionId, 0)));

        mvc.perform(get("/api/v1/jobs/{jobId}/interview-prep", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(questionId.toString()))
                .andExpect(jsonPath("$[0].question").value("Why us?"));
    }

    @Test
    void add_appends_question_after_existing_ones() throws Exception {
        when(manage.getQuestions(jobId, userId))
                .thenReturn(List.of(question(UUID.randomUUID(), 0), question(UUID.randomUUID(), 1)));
        when(manage.saveQuestion(any(InterviewQuestion.class))).thenReturn(question(questionId, 2));

        mvc.perform(post("/api/v1/jobs/{jobId}/interview-prep", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Why us?","category":"motivation"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/jobs/" + jobId + "/interview-prep/" + questionId));

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(manage).saveQuestion(captor.capture());
        InterviewQuestion sent = captor.getValue();
        assertThat(sent.id()).isNull();
        assertThat(sent.jobId()).isEqualTo(jobId);
        assertThat(sent.userId()).isEqualTo(userId);
        assertThat(sent.displayOrder()).isEqualTo(2);
        assertThat(sent.practiced()).isFalse();
    }

    @Test
    void generate_defaults_count_to_ten_when_not_positive() throws Exception {
        when(generate.generateQuestions(jobId, userId, "desc", 10))
                .thenReturn(List.of(question(questionId, 0)));

        mvc.perform(post("/api/v1/jobs/{jobId}/interview-prep/generate", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"desc","count":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(generate).generateQuestions(jobId, userId, "desc", 10);
    }

    @Test
    void generate_passes_explicit_count() throws Exception {
        when(generate.generateQuestions(jobId, userId, null, 4)).thenReturn(List.of());

        mvc.perform(post("/api/v1/jobs/{jobId}/interview-prep/generate", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"count":4}
                                """))
                .andExpect(status().isCreated());

        verify(generate).generateQuestions(jobId, userId, null, 4);
    }

    @Test
    void pack_returns_generated_prep_pack() throws Exception {
        when(generatePrep.generatePrepPack(userId, jobId)).thenReturn(new InterviewPrepPack(
                List.of(question(questionId, 0)), List.of("Stay consistent"), List.of("Team size?")));

        mvc.perform(post("/api/v1/jobs/{jobId}/interview-prep/pack", jobId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.consistencyBrief[0]").value("Stay consistent"))
                .andExpect(jsonPath("$.questionsToAsk[0]").value("Team size?"));
    }

    @Test
    void roleplay_defaults_missing_transcript_and_wrap_up() throws Exception {
        when(mockInterview.respond(userId, jobId, List.of(), false)).thenReturn("Tell me about you");

        mvc.perform(post("/api/v1/jobs/{jobId}/interview-prep/roleplay", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Tell me about you"));

        verify(mockInterview).respond(userId, jobId, List.of(), false);
    }

    @Test
    void roleplay_passes_transcript_and_wrap_up() throws Exception {
        List<MockInterviewTurn> transcript = List.of(new MockInterviewTurn("user", "Hi"));
        when(mockInterview.respond(userId, jobId, transcript, true)).thenReturn("Feedback");

        mvc.perform(post("/api/v1/jobs/{jobId}/interview-prep/roleplay", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"Hi"}],"wrapUp":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Feedback"));
    }

    @Test
    void update_merges_request_over_existing_question() throws Exception {
        InterviewQuestion current = question(questionId, 3);
        when(manage.getQuestions(jobId, userId)).thenReturn(List.of(current));
        when(manage.saveQuestion(any(InterviewQuestion.class))).thenReturn(current);

        mvc.perform(put("/api/v1/jobs/{jobId}/interview-prep/{id}", jobId, questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"starAnswer":"S/T/A/R","practiced":true}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(manage).saveQuestion(captor.capture());
        InterviewQuestion sent = captor.getValue();
        assertThat(sent.id()).isEqualTo(questionId);
        assertThat(sent.question()).isEqualTo("Why us?");
        assertThat(sent.category()).isEqualTo("motivation");
        assertThat(sent.starAnswer()).isEqualTo("S/T/A/R");
        assertThat(sent.practiced()).isTrue();
        assertThat(sent.displayOrder()).isEqualTo(3);
    }

    @Test
    void update_returns_404_when_question_not_in_users_list() throws Exception {
        when(manage.getQuestions(jobId, userId)).thenReturn(List.of(question(UUID.randomUUID(), 0)));

        mvc.perform(put("/api/v1/jobs/{jobId}/interview-prep/{id}", jobId, questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practiced":true}
                                """))
                .andExpect(status().isNotFound());

        verify(manage, never()).saveQuestion(any());
    }

    @Test
    void delete_returns_204_and_deletes_for_current_user() throws Exception {
        mvc.perform(delete("/api/v1/jobs/{jobId}/interview-prep/{id}", jobId, questionId))
                .andExpect(status().isNoContent());

        verify(manage).deleteQuestion(questionId, userId);
    }

    private InterviewQuestion question(UUID id, int order) {
        return new InterviewQuestion(id, jobId, userId, "Why us?", "motivation", null, false,
                order, Instant.now(), Instant.now());
    }
}
