package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.CvTailoringGuidance;
import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TailoredCvGenerator {

    private final ChatProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final PromptCompositionBuilder promptBuilder;

    public TailoredCvGenerator(
            @Qualifier("generationAiProvider") ChatProviderPort aiProvider,
            ObjectMapper objectMapper,
            PromptCompositionBuilder promptBuilder) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Generates tailored CV content using the provided career profile and job description.
     *
     * @param styleTemplate optional {@link PromptTemplate} to customise AI persona and approach;
     *                      pass {@code null} to use the built-in default
     * @param posting       the posting's text, country, and named contact; may be {@link PostingContext#EMPTY}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "objectMapper.writeValueAsString and readValue throw the checked "
                    + "JsonProcessingException; the catch also absorbs runtime failures so a "
                    + "tailoring failure falls back to the untailored master profile.")
    public TailoredCvContent generate(CareerProfileForAi source, PostingContext posting,
                                      String customInstructions, String targetLanguage,
                                      PromptTemplate styleTemplate, CvTailoringGuidance guidance,
                                      List<String> outcomeLessons, String lengthPreference) {
        // Links identify the user, so the model works on a link-free copy; even the fallback below
        // stays link-free because the reviewer pass sends it on to the AI too.
        CareerProfileForAi payload = source.withoutLinks();
        try {
            String sourceJson = objectMapper.writeValueAsString(payload);
            String json = AiResponseParser.extractJsonObject(
                    AiResponseParser.sanitize(aiProvider.generateJson(
                            promptBuilder.composeCvTailoringPrompt(
                                    sourceJson, posting, customInstructions,
                                    targetLanguage, styleTemplate, guidance, outcomeLessons,
                                    lengthPreference), AiOperations.TAILORED_CV)
                    ).trim());
            return objectMapper.readValue(json, TailoredCvContent.class);
        } catch (Exception e) {
            return new TailoredCvContent(
                    payload.profile(),
                    merge(payload.skills(), payload.technologies()),
                    payload.experience(),
                    payload.projects(),
                    payload.education(),
                    payload.certifications(),
                    payload.customSections(),
                    List.of("AI tailoring failed — master profile used without rewriting."));
        }
    }

    private static List<String> merge(List<String> first, List<String> second) {
        java.util.List<String> merged = new java.util.ArrayList<>();
        merged.addAll(first != null ? first : List.of());
        merged.addAll(second != null ? second : List.of());
        return merged.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }
}
