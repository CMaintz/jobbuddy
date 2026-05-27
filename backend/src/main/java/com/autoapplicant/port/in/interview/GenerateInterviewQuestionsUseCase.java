package com.autoapplicant.port.in.interview;

import com.autoapplicant.domain.interview.InterviewQuestion;

import java.util.List;
import java.util.UUID;

public interface GenerateInterviewQuestionsUseCase {
    List<InterviewQuestion> generateQuestions(UUID jobId, UUID userId, String jobDescription, int count);
}
