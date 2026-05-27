package com.autoapplicant.usecase.interview;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.interview.InterviewQuestion;
import com.autoapplicant.port.in.interview.GenerateInterviewQuestionsUseCase;
import com.autoapplicant.port.in.interview.ManageInterviewQuestionsUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import org.springframework.beans.factory.annotation.Qualifier;
import com.autoapplicant.port.out.interview.InterviewQuestionRepositoryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InterviewPrepService implements ManageInterviewQuestionsUseCase, GenerateInterviewQuestionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(InterviewPrepService.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert interview coach. Generate interview questions based on the job description provided.
            Return ONLY a valid JSON array, no markdown, no explanation.
            Each element must have exactly two fields: "question" (string) and "category" (one of: BEHAVIORAL, TECHNICAL, SITUATIONAL, COMPANY).
            Example: [{"question":"Tell me about a time you led a team.","category":"BEHAVIORAL"}]
            """;

    private final InterviewQuestionRepositoryPort repo;
    private final AiProviderPort aiProvider;
    private final ObjectMapper objectMapper;

    public InterviewPrepService(InterviewQuestionRepositoryPort repo,
                                @Qualifier("generationAiProvider") AiProviderPort aiProvider,
                                ObjectMapper objectMapper) {
        this.repo = repo;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<InterviewQuestion> getQuestions(UUID jobId, UUID userId) {
        return repo.findByJobIdAndUserId(jobId, userId);
    }

    @Override
    public InterviewQuestion saveQuestion(InterviewQuestion question) {
        return repo.save(question);
    }

    @Override
    public void deleteQuestion(UUID questionId, UUID userId) {
        repo.deleteByIdAndUserId(questionId, userId);
    }

    @Override
    public List<InterviewQuestion> generateQuestions(UUID jobId, UUID userId, String jobDescription, int count) {
        String userPrompt = String.format(
                "Generate %d interview questions for the following job. Focus on the specific skills, technologies, and responsibilities mentioned.\n\nJob description:\n%s",
                count, jobDescription);

        PromptComposition composition = new PromptComposition(
                SYSTEM_PROMPT, userPrompt, "", "", "", "", userPrompt);

        String json = aiProvider.generateJson(composition);
        List<InterviewQuestion> generated = parseQuestions(json, jobId, userId);
        List<InterviewQuestion> saved = new ArrayList<>();
        int existingCount = repo.findByJobIdAndUserId(jobId, userId).size();
        for (int i = 0; i < generated.size(); i++) {
            saved.add(repo.save(new InterviewQuestion(
                    null, jobId, userId,
                    generated.get(i).question(),
                    generated.get(i).category(),
                    null, false,
                    existingCount + i,
                    null, null)));
        }
        return saved;
    }

    private List<InterviewQuestion> parseQuestions(String json, UUID jobId, UUID userId) {
        List<InterviewQuestion> result = new ArrayList<>();
        try {
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```[a-z]*\\n?", "").replace("```", "").trim();
            }
            JsonNode arr = objectMapper.readTree(cleaned);
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    String question = node.path("question").asText(null);
                    String category = node.path("category").asText("BEHAVIORAL");
                    if (question != null && !question.isBlank()) {
                        result.add(new InterviewQuestion(null, jobId, userId,
                                question, category, null, false, 0, null, null));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI interview questions response: {}", e.getMessage());
        }
        return result;
    }
}
