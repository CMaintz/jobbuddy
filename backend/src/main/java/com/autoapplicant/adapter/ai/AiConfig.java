package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.ai.ChatProviderPort;
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
        String provider = props.getAi().getEnrichmentProvider();
        String tier = props.getAi().getEnrichmentTier();
        return "gemini".equalsIgnoreCase(provider)
                ? new GeminiAdapter(gemini, props, tierModel("gemini", tier, props))
                : new OpenAiAdapter(openAi, props, tierModel("openai", tier, props));
    }

    @Bean("generationAiProvider")
    public ChatProviderPort generationAiProvider(
            @Qualifier("openAiHttpClient") OpenAIClient openAi,
            @Qualifier("geminiHttpClient") OpenAIClient gemini,
            AppProperties props) {
        String provider = props.getAi().getGenerationProvider();
        if (isCliProvider(provider)) {
            return new CliAgentAdapter(props);
        }
        String tier = props.getAi().getGenerationTier();
        return "gemini".equalsIgnoreCase(provider)
                ? new GeminiAdapter(gemini, props, tierModel("gemini", tier, props))
                : new OpenAiAdapter(openAi, props, tierModel("openai", tier, props));
    }

    /** CLI-agent generation (Claude Code / Codex) — cheap flat-fee path. Never used for embeddings. */
    private static boolean isCliProvider(String provider) {
        return provider != null && (provider.equalsIgnoreCase("claude-cli")
                || provider.equalsIgnoreCase("cli")
                || provider.equalsIgnoreCase("codex"));
    }

    /**
     * Resolves the chat model for a provider + spend tier (economy/standard/premium),
     * falling back to the provider's default {@code model} when a tier model is unset.
     */
    private static String tierModel(String provider, String tier, AppProperties props) {
        String t = tier == null ? "standard" : tier.trim().toLowerCase();
        if ("openai".equalsIgnoreCase(provider)) {
            var o = props.getOpenai();
            String tierModel = switch (t) {
                case "economy" -> o.getEconomyModel();
                case "premium" -> o.getPremiumModel();
                default -> o.getStandardModel();
            };
            return firstNonBlank(tierModel, o.getModel());
        }
        var g = props.getGemini();
        String tierModel = switch (t) {
            case "economy" -> g.getEconomyModel();
            case "premium" -> g.getPremiumModel();
            default -> g.getStandardModel();
        };
        return firstNonBlank(tierModel, g.getModel());
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
