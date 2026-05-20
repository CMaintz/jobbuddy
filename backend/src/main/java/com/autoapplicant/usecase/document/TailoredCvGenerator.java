package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TailoredCvGenerator {

    private final AiProviderPort aiProvider;
    private final ObjectMapper objectMapper;

    public TailoredCvGenerator(AiProviderPort aiProvider, ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    public TailoredCvContent generate(CareerProfileForAi source, String jobDescription,
                                       String customInstructions, String targetLanguage) {
        try {
            String sourceJson = objectMapper.writeValueAsString(source);
            String languageInstruction = targetLanguage != null && !targetLanguage.isBlank()
                    ? "Write all rewritten text in " + targetLanguage + "."
                    : "Write rewritten text in the same language as the job description when clear.";

            String prompt = """
                    You tailor CV content from a structured master career profile.
                    The profile intentionally excludes the user's name and contact information. Do not ask for it, infer it, or invent it.
                    Return only valid JSON matching this shape:
                    {
                      "selectedProfile": "role-specific profile text",
                      "selectedSkills": ["skill"],
                      "experience": [{"sourceId":"existing id","title":"...","subtitle":"...","location":"...","dateRange":"...","description":"...","bullets":["..."],"technologies":["..."],"links":[]}],
                      "projects": [],
                      "education": [],
                      "certifications": [],
                      "keywordCoverage": 0,
                      "matchedKeywords": [],
                      "missingKeywords": [],
                      "notes": []
                    }
                    Use only source facts. You may rewrite profile text, descriptions, and bullets, but must keep sourceId values from the provided profile.
                    Do not invent employers, titles, dates, schools, credentials, technologies, outcomes, or links.
                    """ + "\n" + languageInstruction + "\n\n## Contact-free master career profile JSON\n" + sourceJson +
                    "\n\n## Job description\n" + nullToEmpty(jobDescription) +
                    "\n\n## Additional instructions\n" + nullToEmpty(customInstructions);

            PromptComposition composition = new PromptComposition(
                    "You are a careful CV tailoring engine. Return JSON only.",
                    prompt, "", "", "", "", prompt);
            String json = extractJsonObject(aiProvider.generateJson(composition).trim());
            return objectMapper.readValue(json, TailoredCvContent.class);
        } catch (Exception e) {
            return new TailoredCvContent(
                    source.profile(),
                    merge(source.skills(), source.technologies()),
                    source.experience(),
                    source.projects(),
                    source.education(),
                    source.certifications(),
                    0,
                    List.of(),
                    List.of(),
                    List.of("AI tailoring failed, so the master profile was used without rewriting."));
        }
    }

    private static String extractJsonObject(String value) {
        String json = stripCodeFence(value);
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("AI response was not a JSON object");
        }
        return json.substring(first, last + 1).trim();
    }

    private static String stripCodeFence(String value) {
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                return value.substring(firstLine + 1, lastFence).trim();
            }
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static List<String> merge(List<String> first, List<String> second) {
        java.util.List<String> merged = new java.util.ArrayList<>();
        merged.addAll(first != null ? first : List.of());
        merged.addAll(second != null ? second : List.of());
        return merged.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
    }
}
