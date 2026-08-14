package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai/";

    @Bean("openAiHttpClient")
    public OpenAIClient openAiHttpClient(AppProperties props) {
        return OpenAIOkHttpClient.builder()
                .apiKey(props.getOpenai().getApiKey() != null ? props.getOpenai().getApiKey() : "")
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean("geminiHttpClient")
    public OpenAIClient geminiHttpClient(AppProperties props) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(GEMINI_BASE_URL)
                .apiKey(props.getGemini().getApiKey() != null ? props.getGemini().getApiKey() : "")
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean("enrichmentAiProvider")
    public AiProviderPort enrichmentAiProvider(
            @Qualifier("openAiHttpClient") OpenAIClient openAi,
            @Qualifier("geminiHttpClient") OpenAIClient gemini,
            AppProperties props) {
        return "gemini".equalsIgnoreCase(props.getAi().getEnrichmentProvider())
                ? new GeminiAdapter(gemini, props)
                : new OpenAiAdapter(openAi, props);
    }

    @Bean("generationAiProvider")
    public AiProviderPort generationAiProvider(
            @Qualifier("openAiHttpClient") OpenAIClient openAi,
            @Qualifier("geminiHttpClient") OpenAIClient gemini,
            AppProperties props) {
        String provider = props.getAi().getGenerationProvider();
        if (isCliProvider(provider)) {
            return new CliAgentAdapter(props);
        }
        return "gemini".equalsIgnoreCase(provider)
                ? new GeminiAdapter(gemini, props)
                : new OpenAiAdapter(openAi, props);
    }

    /** CLI-agent generation (Claude Code / Codex) — cheap flat-fee path. Never used for embeddings. */
    private static boolean isCliProvider(String provider) {
        return provider != null && (provider.equalsIgnoreCase("claude-cli")
                || provider.equalsIgnoreCase("cli")
                || provider.equalsIgnoreCase("codex"));
    }
}
