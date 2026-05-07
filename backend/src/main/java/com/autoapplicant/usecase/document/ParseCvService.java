package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ParseCvService implements ParseCvUseCase {

    private static final Logger log = LoggerFactory.getLogger(ParseCvService.class);

    private static final String SYSTEM_PROMPT = """
            You are a CV parser. Extract structured career information from the provided CV text.
            Return ONLY valid JSON matching this structure (omit null fields):
            {
              "fullName": "string",
              "headline": "string",
              "summary": "string",
              "location": "string",
              "linkedinUrl": "string",
              "githubUrl": "string",
              "websiteUrl": "string",
              "skills": ["string"],
              "technologies": ["string"],
              "languages": ["string"]
            }
            """;

    private final AiProviderPort aiProvider;
    private final ObjectMapper objectMapper;

    public ParseCvService(AiProviderPort aiProvider, ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public Profile parseCvText(UUID userId, String rawCvText) {
        try {
            PromptComposition composition = new PromptComposition(
                    SYSTEM_PROMPT, rawCvText, null, null, null, null, null);
            String json = aiProvider.generate(composition);

            // Strip markdown code fences if present
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```[a-z]*\n?", "").replaceAll("```", "").trim();
            }

            JsonNode node = objectMapper.readTree(cleaned);
            return new Profile(
                    null, userId,
                    textOrNull(node, "fullName"),
                    textOrNull(node, "headline"),
                    textOrNull(node, "summary"),
                    textOrNull(node, "location"),
                    null,
                    textOrNull(node, "linkedinUrl"),
                    textOrNull(node, "githubUrl"),
                    textOrNull(node, "websiteUrl"),
                    null,
                    arrayOrEmpty(node, "skills"),
                    arrayOrEmpty(node, "technologies"),
                    arrayOrEmpty(node, "languages"),
                    null, null, "DKK", null, null, null, null
            );
        } catch (Exception e) {
            log.error("CV parsing failed for user {}: {}", userId, e.getMessage());
            return new Profile(null, userId, null, null, null, null, null,
                    null, null, null, null, List.of(), List.of(), List.of(),
                    null, null, "DKK", null, null, null, null);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private static List<String> arrayOrEmpty(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || !child.isArray()) return List.of();
        String[] values = new String[child.size()];
        for (int i = 0; i < child.size(); i++) values[i] = child.get(i).asText();
        return Arrays.asList(values);
    }
}
