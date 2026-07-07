package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TailoredCvGenerator {

    private final AiProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final PromptCompositionBuilder promptBuilder;

    public TailoredCvGenerator(
            @Qualifier("generationAiProvider") AiProviderPort aiProvider,
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
     */
    public TailoredCvContent generate(CareerProfileForAi source, String jobDescription,
                                      String customInstructions, String targetLanguage,
                                      PromptTemplate styleTemplate, WritingProfile writingProfile) {
        try {
            String sourceJson = objectMapper.writeValueAsString(source);
            String json = AiResponseParser.extractJsonObject(
                    aiProvider.generateJson(
                            promptBuilder.composeCvTailoringPrompt(
                                    sourceJson, jobDescription, customInstructions,
                                    targetLanguage, styleTemplate, writingProfile)
                    ).trim());
            return objectMapper.readValue(json, TailoredCvContent.class);
        } catch (Exception e) {
            return new TailoredCvContent(
                    source.profile(),
                    merge(source.skills(), source.technologies()),
                    source.experience(),
                    source.projects(),
                    source.education(),
                    source.certifications(),
                    0, List.of(), List.of(),
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
