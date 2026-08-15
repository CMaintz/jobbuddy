package com.autoapplicant.usecase.interview;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.interview.InterviewPrepPack;
import com.autoapplicant.domain.interview.InterviewQuestion;
import com.autoapplicant.domain.interview.MockInterviewTurn;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.interview.GenerateInterviewPrepUseCase;
import com.autoapplicant.port.in.interview.GenerateInterviewQuestionsUseCase;
import com.autoapplicant.port.in.interview.ManageInterviewQuestionsUseCase;
import com.autoapplicant.port.in.interview.MockInterviewUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import org.springframework.beans.factory.annotation.Qualifier;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.port.out.interview.InterviewQuestionRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InterviewPrepService implements ManageInterviewQuestionsUseCase,
        GenerateInterviewQuestionsUseCase, GenerateInterviewPrepUseCase, MockInterviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(InterviewPrepService.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert interview coach. Generate interview questions based on the job description provided.
            Return ONLY a valid JSON array, no markdown, no explanation.
            Each element must have exactly two fields: "question" (string) and "category" (one of: BEHAVIORAL, TECHNICAL, SITUATIONAL, COMPANY).
            Example: [{"question":"Tell me about a time you led a team.","category":"BEHAVIORAL"}]
            """;

    private final InterviewQuestionRepositoryPort repo;
    private final ChatProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final JobRepositoryPort jobRepo;
    private final GeneratedDocumentRepositoryPort documentRepo;
    private final CareerProfileContextService careerProfileContext;

    public InterviewPrepService(InterviewQuestionRepositoryPort repo,
                                @Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                                ObjectMapper objectMapper,
                                JobRepositoryPort jobRepo,
                                GeneratedDocumentRepositoryPort documentRepo,
                                CareerProfileContextService careerProfileContext) {
        this.repo = repo;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.jobRepo = jobRepo;
        this.documentRepo = documentRepo;
        this.careerProfileContext = careerProfileContext;
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

    @Override
    public InterviewPrepPack generatePrepPack(UUID userId, UUID jobId) {
        Job job = jobRepo.findById(jobId).orElseThrow(
                () -> new IllegalArgumentException("Job not found"));
        String jobDescription = job.descriptionClean() != null ? job.descriptionClean() : "";

        // Documents actually generated for this job — source of the consistency brief
        String sentDocuments = documentRepo.findByJobId(jobId).stream()
                .filter(d -> d.userId().equals(userId) && d.content() != null && !d.content().isBlank())
                .limit(3)
                .map(d -> "### " + d.documentType() + "\n"
                        + d.content().substring(0, Math.min(3000, d.content().length())))
                .reduce("", (a, b) -> a + "\n\n" + b);

        String profileJson = careerProfileContext.buildJson(userId);

        String systemPrompt = """
                You are an expert interview coach preparing a candidate for an interview.
                Never invent experience the candidate does not have; where the profile shows a gap
                against the posting, prepare the candidate to address it honestly.
                Return ONLY valid JSON — no markdown, no commentary.""";

        String userPrompt = """
                Build an interview prep pack.

                Return only valid JSON in exactly this shape:
                {
                  "questions": [{"question": "...", "category": "BEHAVIORAL|TECHNICAL|SITUATIONAL|COMPANY"}],
                  "consistencyBrief": ["<a claim made in the candidate's submitted documents they must be ready to defend — quote or paraphrase it>"],
                  "questionsToAsk": ["<a sharp question the candidate should ask the interviewer>"]
                }
                Give 8-10 questions targeting the posting's requirements and the candidate's weakest
                coverage of them; 3-6 consistency-brief entries (omit the field's entries if no documents
                are provided); and 4-6 questions to ask.

                ## Job (%s at %s)
                %s

                ## Candidate profile (contact-free)
                %s
                %s""".formatted(
                job.title(), job.companyName() != null ? job.companyName() : "unknown company",
                jobDescription,
                profileJson,
                sentDocuments.isBlank() ? "" : "\n## Documents the candidate submitted\n" + sentDocuments);

        PromptComposition composition = new PromptComposition(
                systemPrompt, userPrompt, "", "", "", "", userPrompt);
        JsonNode root;
        try {
            String cleaned = aiProvider.generateJson(composition).trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```[a-z]*\\n?", "").replace("```", "").trim();
            }
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("Prep pack generation failed for job {}: {}", jobId, e.getMessage());
            throw new IllegalStateException("Prep pack generation failed", e);
        }

        List<InterviewQuestion> saved = new ArrayList<>();
        int order = repo.findByJobIdAndUserId(jobId, userId).size();
        for (JsonNode node : root.path("questions")) {
            String question = node.path("question").asText(null);
            if (question == null || question.isBlank()) continue;
            saved.add(repo.save(new InterviewQuestion(null, jobId, userId,
                    question, node.path("category").asText("BEHAVIORAL"),
                    null, false, order++, null, null)));
        }

        return new InterviewPrepPack(saved, textList(root, "consistencyBrief"), textList(root, "questionsToAsk"));
    }

    @Override
    public String respond(UUID userId, UUID jobId, List<MockInterviewTurn> transcript, boolean wrapUp) {
        Job job = jobRepo.findById(jobId).orElseThrow(
                () -> new IllegalArgumentException("Job not found"));
        String jobDescription = job.descriptionClean() != null ? job.descriptionClean() : "";
        String profileJson = careerProfileContext.buildJson(userId);
        String company = job.companyName() != null ? job.companyName() : "the company";

        String systemPrompt = """
                You are roleplaying as an experienced hiring manager at %s interviewing a candidate \
                for the role of %s. Stay fully in character: professional, friendly but probing. \
                Ask ONE question or follow-up at a time; react naturally to the candidate's previous \
                answer before moving on. Draw questions from the job description and dig into areas \
                where the candidate's profile looks weakest against it. Keep each message under \
                120 words. Never break character, never mention being an AI, and output plain \
                conversational text only — no JSON, no markdown headers.""".formatted(company, job.title());

        StringBuilder convo = new StringBuilder();
        for (MockInterviewTurn turn : transcript) {
            if (turn == null || turn.content() == null || turn.content().isBlank()) continue;
            convo.append("candidate".equalsIgnoreCase(turn.role()) ? "Candidate: " : "Interviewer: ")
                 .append(turn.content().strip()).append("\n\n");
        }

        String instruction;
        if (wrapUp) {
            systemPrompt = """
                    You are an expert interview coach. The mock interview below has just ended. \
                    Give the candidate honest, specific feedback on their answers: what landed, \
                    what fell flat, and how to improve each weak answer — quote their own words \
                    where useful. Be encouraging but do not sugar-coat. End with the three changes \
                    that would most improve their next real interview. Plain text, short paragraphs.""";
            instruction = "Give your coaching feedback on the interview.";
        } else if (convo.isEmpty()) {
            instruction = "Open the interview: greet the candidate briefly and ask your first question.";
        } else {
            instruction = "Continue as the interviewer: respond to the candidate's last answer.";
        }

        String userPrompt = """
                ## Job (%s at %s)
                %s

                ## Candidate profile (contact-free)
                %s

                ## Interview so far
                %s
                %s""".formatted(job.title(), company, jobDescription, profileJson,
                convo.isEmpty() ? "(not started)" : convo.toString().strip(), instruction);

        PromptComposition composition = new PromptComposition(
                systemPrompt, userPrompt, "", "", "", "", userPrompt);
        return aiProvider.generate(composition).strip();
    }

    private static List<String> textList(JsonNode root, String field) {
        List<String> out = new ArrayList<>();
        root.path(field).forEach(n -> {
            String text = n.asText(null);
            if (text != null && !text.isBlank()) out.add(text);
        });
        return out;
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
