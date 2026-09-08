package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.interview.InterviewPrepPack;
import com.autoapplicant.domain.interview.InterviewQuestion;
import com.autoapplicant.domain.interview.MockInterviewTurn;
import com.autoapplicant.port.in.interview.GenerateInterviewPrepUseCase;
import com.autoapplicant.port.in.interview.GenerateInterviewQuestionsUseCase;
import com.autoapplicant.port.in.interview.ManageInterviewQuestionsUseCase;
import com.autoapplicant.port.in.interview.MockInterviewUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/interview-prep")
@Tag(name = "Interview Prep")
public class InterviewPrepController {

    public record AddQuestionRequest(String question, String category) {}
    public record UpdateQuestionRequest(String question, String category, String starAnswer, Boolean practiced) {}
    public record GenerateRequest(String jobDescription, int count) {}
    public record RoleplayRequest(List<MockInterviewTurn> messages, Boolean wrapUp) {}
    public record RoleplayResponse(String reply) {}

    private final ManageInterviewQuestionsUseCase manage;
    private final GenerateInterviewQuestionsUseCase generate;
    private final GenerateInterviewPrepUseCase generatePrep;
    private final MockInterviewUseCase mockInterview;
    private final SecurityContextHelper secCtx;

    public InterviewPrepController(ManageInterviewQuestionsUseCase manage,
                                   GenerateInterviewQuestionsUseCase generate,
                                   GenerateInterviewPrepUseCase generatePrep,
                                   MockInterviewUseCase mockInterview,
                                   SecurityContextHelper secCtx) {
        this.manage = manage;
        this.generate = generate;
        this.generatePrep = generatePrep;
        this.mockInterview = mockInterview;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List interview questions for a job")
    @GetMapping
    public ResponseEntity<List<InterviewQuestion>> list(@PathVariable UUID jobId) {
        return ResponseEntity.ok(manage.getQuestions(jobId, secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Add an interview question manually")
    @ApiResponse(responseCode = "201", description = "Question added")
    @PostMapping
    public ResponseEntity<InterviewQuestion> add(@PathVariable UUID jobId,
                                                  @RequestBody AddQuestionRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        int order = manage.getQuestions(jobId, userId).size();
        InterviewQuestion saved = manage.saveQuestion(new InterviewQuestion(
                null, jobId, userId, req.question(), req.category(), null, false, order, null, null));
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + jobId + "/interview-prep/" + saved.id()))
                .body(saved);
    }

    @Operation(summary = "Generate interview questions with AI")
    @ApiResponse(responseCode = "201", description = "Questions generated")
    @PostMapping("/generate")
    public ResponseEntity<List<InterviewQuestion>> generateQuestions(
            @PathVariable UUID jobId,
            @RequestBody GenerateRequest req) {
        List<InterviewQuestion> questions = generate.generateQuestions(
                jobId, secCtx.getCurrentUserId(), req.jobDescription(),
                req.count() > 0 ? req.count() : 10);
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + jobId + "/interview-prep"))
                .body(questions);
    }

    @Operation(summary = "Generate a full prep pack: gap-targeted questions, consistency brief, questions to ask")
    @ApiResponse(responseCode = "201", description = "Prep pack generated")
    @PostMapping("/pack")
    public ResponseEntity<InterviewPrepPack> generatePack(@PathVariable UUID jobId) {
        InterviewPrepPack pack = generatePrep.generatePrepPack(secCtx.getCurrentUserId(), jobId);
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + jobId + "/interview-prep")).body(pack);
    }

    @Operation(summary = "Mock-interview roleplay turn — send the full transcript, get the interviewer's next message (or coaching feedback when wrapUp)")
    @PostMapping("/roleplay")
    public ResponseEntity<RoleplayResponse> roleplay(@PathVariable UUID jobId,
                                                     @RequestBody RoleplayRequest req) {
        String reply = mockInterview.respond(
                secCtx.getCurrentUserId(), jobId,
                req.messages() != null ? req.messages() : List.of(),
                Boolean.TRUE.equals(req.wrapUp()));
        return ResponseEntity.ok(new RoleplayResponse(reply));
    }

    @Operation(summary = "Update an interview question (save STAR answer, mark practiced)")
    @PutMapping("/{id}")
    public ResponseEntity<InterviewQuestion> update(@PathVariable UUID jobId,
                                                     @PathVariable UUID id,
                                                     @RequestBody UpdateQuestionRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        List<InterviewQuestion> existing = manage.getQuestions(jobId, userId);
        InterviewQuestion current = existing.stream()
                .filter(q -> q.id().equals(id)).findFirst().orElse(null);
        if (current == null) return ResponseEntity.notFound().build();

        InterviewQuestion updated = manage.saveQuestion(new InterviewQuestion(
                id, jobId, userId,
                req.question() != null ? req.question() : current.question(),
                req.category() != null ? req.category() : current.category(),
                req.starAnswer() != null ? req.starAnswer() : current.starAnswer(),
                req.practiced() != null ? req.practiced() : current.practiced(),
                current.displayOrder(),
                current.createdAt(), null));
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete an interview question")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID jobId, @PathVariable UUID id) {
        manage.deleteQuestion(id, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
