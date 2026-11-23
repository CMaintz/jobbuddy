package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Rewrites a letter the gate failed, from the gate's quotes. It sees the letter, the needs and
 * what failed, never the profile: a rewrite may reshape or cut what the letter says, and anything
 * it added could not be checked against the profile here. The content guards still run on its
 * output, after the loop.
 */
@Component
public class LetterRewriter {

    private static final String PERSONA = "You are an experienced writer of job applications that get "
            + "people invited to interview. You fix a letter a strict reviewer failed. Respond with ONLY "
            + "valid JSON.";

    private static final String TASK = """
            ## Task
            Rewrite the letter so that every point below passes. Fix each quoted sentence: turn it \
            forward, tie it to an employer need, or cut it. Keep everything that already works. \
            Reshape and cut only: never add a fact, a number, an employer, a title or a claim that \
            the current letter does not contain. Keep the letter's language and length.""";

    private static final String SHAPE =
            "Return only valid JSON matching exactly this shape:\n{\"body\": \"<the full rewritten letter>\"}";

    private final ChatProviderPort provider;
    private final ObjectMapper json;

    public LetterRewriter(@Qualifier("generationAiProvider") ChatProviderPort provider, ObjectMapper json) {
        this.provider = provider;
        this.json = json;
    }

    /**
     * The rewritten letter.
     *
     * @throws IllegalStateException when the provider fails or its answer holds no letter
     */
    public String rewrite(String letter, List<String> needs, LetterGateResult failed, LetterBrief brief) {
        GenerationGuardrails guardrails = brief.guardrails();
        String system = PERSONA + "\n\n" + guardrails.untrustedInputBlock()
                + (guardrails.resolvedLanguage() != null
                    ? " Write the letter in " + guardrails.resolvedLanguage() + "." : "");
        String user = Stream.of(
                        TASK,
                        LetterRules.BLOCK,
                        guardrails.honestyRules(),
                        guardrails.marketRules(),
                        guardrails.clicheBlock(),
                        LetterRules.needsBlock(needs),
                        LetterGate.failures(failed),
                        "## Current Letter\n" + letter,
                        "## Job Description\n" + brief.postingOr("(no job description provided)"),
                        SHAPE)
                .filter(block -> block != null && !block.isBlank())
                .collect(Collectors.joining("\n\n"));
        return parse(provider.generateJson(new PromptComposition(system, user, "", "", "", "", user),
                AiOperations.LETTER_REWRITE));
    }

    private String parse(String answer) {
        try {
            Rewrite parsed = json.readValue(
                    AiResponseParser.extractJsonObject(AiResponseParser.sanitize(answer).trim()), Rewrite.class);
            if (parsed.body() == null || parsed.body().isBlank()) {
                throw new IllegalStateException("The rewrite holds no letter");
            }
            return parsed.body().strip();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The rewrite could not be read", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rewrite(String body) {}
}
