package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.in.document.AnalyzeWritingStyleUseCase;
import com.autoapplicant.port.in.document.ManageWritingProfileUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WritingProfileService implements ManageWritingProfileUseCase, AnalyzeWritingStyleUseCase {

    private static final Logger log = LoggerFactory.getLogger(WritingProfileService.class);

    private static final int MAX_SAMPLES = 5;
    private static final int MAX_SAMPLE_CHARS = 8000;
    private static final int MIN_TOTAL_CHARS = 150;

    /** Samples are user-supplied text sent to the AI as-is; contact lines are stripped first. */
    private static final Pattern CONTACT_LINE = Pattern.compile(
            "[\\w.+-]+@[\\w-]+\\.[\\w.]+|https?://\\S+|www\\.\\S+|\\+?\\d[\\d ().-]{7,}\\d");

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            You are a writing-style analyst. You will receive texts a job seeker wrote themselves.
            Describe HOW this person writes — never WHAT they wrote about. Do not copy job-specific
            content, company names, or biographical claims into the style rules. Base every
            observation on the samples; do not invent preferences they show no evidence of.
            Return ONLY valid JSON — no markdown, no commentary.""";

    private final WritingProfileRepositoryPort repo;
    private final GeneratedDocumentRepositoryPort documentRepo;
    private final AiProviderPort aiProvider;
    private final ObjectMapper objectMapper;

    public WritingProfileService(WritingProfileRepositoryPort repo,
                                 GeneratedDocumentRepositoryPort documentRepo,
                                 @Qualifier("generationAiProvider") AiProviderPort aiProvider,
                                 ObjectMapper objectMapper) {
        this.repo = repo;
        this.documentRepo = documentRepo;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WritingProfile> get(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public WritingProfile save(WritingProfile profile) {
        return repo.save(profile);
    }

    @Override
    public WritingProfile analyze(UUID userId, List<String> samples, List<UUID> documentIds) {
        List<String> cleaned = collectSamples(userId, samples, documentIds);

        StringBuilder sampleBlock = new StringBuilder();
        for (int i = 0; i < cleaned.size(); i++) {
            sampleBlock.append("\n\n## Sample ").append(i + 1).append("\n").append(cleaned.get(i));
        }

        String userPrompt = """
                Analyze the writing style in the samples below and return only valid JSON in exactly this shape:
                {
                  "tone": "<1-2 sentences describing the overall tone>",
                  "vocabularyNotes": "<the word choices this writer favours and avoids>",
                  "phrasingPatterns": ["<a recurring turn of phrase or sentence pattern, verbatim where possible>"],
                  "dos": ["<a short imperative style rule this writer clearly follows>"],
                  "donts": ["<a short imperative rule for what this writer avoids>"],
                  "structureNotes": "<how they build a text: opening, paragraph flow, length, sign-off>",
                  "exampleExcerpts": ["<a verbatim excerpt of 1-3 sentences that best shows the voice>"]
                }
                Give 3-6 phrasingPatterns, 3-6 dos, 3-6 donts, and 2-4 exampleExcerpts (each under 300 characters).
                Every rule must be short enough to follow while writing — one clause, no explanations.""" + sampleBlock;

        PromptComposition composition = new PromptComposition(
                ANALYSIS_SYSTEM_PROMPT, userPrompt, "", "", "", "", userPrompt);

        JsonNode root;
        try {
            root = objectMapper.readTree(AiResponseParser.extractJsonObject(aiProvider.generateJson(composition)));
        } catch (Exception e) {
            log.warn("Writing-style analysis failed for user {}: {}", userId, e.getMessage());
            throw new IllegalStateException("Writing-style analysis failed", e);
        }

        Optional<WritingProfile> existing = repo.findByUserId(userId);
        return new WritingProfile(
                existing.map(WritingProfile::id).orElse(null), userId,
                text(root, "tone"), text(root, "vocabularyNotes"),
                textList(root, "phrasingPatterns"), textList(root, "exampleExcerpts"),
                textList(root, "dos"), textList(root, "donts"),
                text(root, "structureNotes"),
                Instant.now(),
                existing.map(WritingProfile::createdAt).orElse(null), null);
    }

    private List<String> collectSamples(UUID userId, List<String> samples, List<UUID> documentIds) {
        List<String> collected = new ArrayList<>();
        if (samples != null) {
            samples.stream().filter(s -> s != null && !s.isBlank()).forEach(collected::add);
        }
        if (documentIds != null) {
            documentIds.stream()
                    .map(id -> documentRepo.findByIdAndUserId(id, userId))
                    .flatMap(Optional::stream)
                    .map(GeneratedDocument::content)
                    .filter(c -> c != null && !c.isBlank())
                    .forEach(collected::add);
        }

        List<String> cleaned = collected.stream()
                .limit(MAX_SAMPLES)
                .map(WritingProfileService::stripContactLines)
                .map(s -> s.length() > MAX_SAMPLE_CHARS ? s.substring(0, MAX_SAMPLE_CHARS) : s)
                .filter(s -> !s.isBlank())
                .toList();

        int total = cleaned.stream().mapToInt(String::length).sum();
        if (total < MIN_TOTAL_CHARS) {
            throw new IllegalArgumentException("Provide at least a paragraph of your own writing to analyze");
        }
        return cleaned;
    }

    private static String stripContactLines(String sample) {
        return sample.lines()
                .filter(line -> !CONTACT_LINE.matcher(line).find())
                .reduce(new StringBuilder(), (sb, line) -> sb.append(line).append('\n'), StringBuilder::append)
                .toString().strip();
    }

    private static String text(JsonNode root, String field) {
        String value = root.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static List<String> textList(JsonNode root, String field) {
        List<String> out = new ArrayList<>();
        root.path(field).forEach(n -> {
            String value = n.asText(null);
            if (value != null && !value.isBlank()) out.add(value.strip());
        });
        return out;
    }
}
