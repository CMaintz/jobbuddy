package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.in.user.ParseLinkedInProfileUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LinkedInProfileParseService implements ParseLinkedInProfileUseCase {

    private final ChatProviderPort aiProvider;

    public LinkedInProfileParseService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider) {
        this.aiProvider = aiProvider;
    }

    @Override
    public String parseProfileFromText(UUID userId, String extractedText) {
        String systemPrompt = "You are a data extraction assistant. Extract structured information from a LinkedIn profile PDF export. Return ONLY valid JSON, no markdown.";
        String userPrompt = "Extract the following fields from this LinkedIn profile PDF and return as JSON:\n" +
                "{\n" +
                "  \"fullName\": \"\",\n" +
                "  \"headline\": \"\",\n" +
                "  \"summary\": \"\",\n" +
                "  \"location\": \"\",\n" +
                "  \"skills\": [],\n" +
                "  \"experience\": [{\"title\":\"\",\"company\":\"\",\"startDate\":\"\",\"endDate\":\"\",\"description\":\"\"}],\n" +
                "  \"education\": [{\"institution\":\"\",\"degree\":\"\",\"fieldOfStudy\":\"\",\"startDate\":\"\",\"endDate\":\"\"}],\n" +
                "  \"certifications\": [{\"name\":\"\",\"issuer\":\"\",\"issuedAt\":\"\"}]\n" +
                "}\n\nPDF content:\n" + extractedText;

        PromptComposition composition = new PromptComposition(
                systemPrompt,
                userPrompt,
                "",
                "",
                "",
                "",
                userPrompt
        );

        return aiProvider.generate(composition);
    }
}
