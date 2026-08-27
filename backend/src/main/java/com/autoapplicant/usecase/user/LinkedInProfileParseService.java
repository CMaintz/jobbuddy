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
        String systemPrompt = "You are a data extraction assistant. Extract structured information "
                + "from a LinkedIn profile PDF export. Extract only what the export states — never "
                + "infer a date, expand an abbreviation, or improve a title. Omit anything the "
                + "document does not contain. What you return becomes the candidate's profile, and "
                + "later checks treat it as their own account of themselves: an invented detail here "
                + "is one nothing downstream can catch. Return ONLY valid JSON, no markdown.";
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
