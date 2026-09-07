package com.autoapplicant.port.in.interview;

import com.autoapplicant.domain.interview.InterviewQuestion;

import java.util.List;
import java.util.UUID;

public interface ManageInterviewQuestionsUseCase {
    List<InterviewQuestion> getQuestions(UUID jobId, UUID userId);
    InterviewQuestion saveQuestion(InterviewQuestion question);
    void deleteQuestion(UUID questionId, UUID userId);
}
