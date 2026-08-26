package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import org.springframework.beans.factory.annotation.Qualifier;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
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

    private final ChatProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final ProfilePrivateInfoRepositoryPort privateInfoRepo;
    private final ProfileSocialRepositoryPort socialRepo;

    public ParseCvService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider, ObjectMapper objectMapper,
                          ProfilePrivateInfoRepositoryPort privateInfoRepo,
                          ProfileSocialRepositoryPort socialRepo) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.privateInfoRepo = privateInfoRepo;
        this.socialRepo = socialRepo;
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

            // Save PII separately
            String fullName = textOrNull(node, "fullName");
            String location  = textOrNull(node, "location");
            if (fullName != null || location != null) {
                ProfilePrivateInfo existing = privateInfoRepo.findByUserId(userId).orElse(null);
                if (existing == null) {
                    privateInfoRepo.save(new ProfilePrivateInfo(null, userId,
                            fullName, null, null, location, null, null, null, null));
                } else {
                    privateInfoRepo.save(new ProfilePrivateInfo(existing.id(), userId,
                            fullName != null ? fullName : existing.fullName(),
                            existing.phone(), existing.photoUrl(),
                            location != null ? location : existing.location(),
                            existing.municipality(), existing.contactEmail(),
                            existing.createdAt(), null));
                }
            }

            // Save social links separately
            int order = socialRepo.findByUserId(userId).size();
            saveSocialIfPresent(userId, node, "linkedinUrl", "LinkedIn", "linkedin", order);
            saveSocialIfPresent(userId, node, "githubUrl",   "GitHub",   "github",   order + 1);
            saveSocialIfPresent(userId, node, "websiteUrl",  "Website",  "globe",    order + 2);

            return new Profile(
                    null, userId,
                    textOrNull(node, "headline"),
                    textOrNull(node, "summary"),
                    null,
                    arrayOrEmpty(node, "skills"),
                    arrayOrEmpty(node, "technologies"),
                    arrayOrEmpty(node, "languages"),
                    arrayOrEmpty(node, "interests"),
                    null, null, "DKK", null, null, null, null
            );
        } catch (Exception e) {
            log.error("CV parsing failed for user {}: {}", userId, e.getMessage());
            return new Profile(null, userId, null, null, null, List.of(), List.of(), List.of(),
                    List.of(), null, null, "DKK", null, null, null, null);
        }
    }

    private void saveSocialIfPresent(UUID userId, JsonNode node, String field,
                                      String platform, String iconKey, int displayOrder) {
        String url = textOrNull(node, field);
        if (url == null || url.isBlank()) return;
        List<ProfileSocial> existing = socialRepo.findByUserId(userId);
        boolean alreadyExists = existing.stream()
                .anyMatch(s -> iconKey.equalsIgnoreCase(s.iconKey()));
        if (!alreadyExists) {
            socialRepo.save(new ProfileSocial(null, userId, platform, url, null,
                    iconKey, displayOrder, null, null));
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
