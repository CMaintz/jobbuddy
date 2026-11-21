package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.in.ai.ManageAiCredentialUseCase;
import com.autoapplicant.port.in.ai.ResolveEmbeddingChoiceUseCase;
import com.autoapplicant.port.out.ai.AiUsageRepositoryPort;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.ai.CurrentUserPort;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AiConfig.class);

    static final String GEMINI_BASE_URL =
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

    /**
     * Every embedding in the process: postings, her profile, search queries. Always a real API
     * (a local CLI agent has no embedding endpoint), and always the install's stored choice, so
     * the desktop app and the scheduled crawl embed with the same model (EmbeddingChoiceService).
     */
    @Bean("embeddingProvider")
    public EmbeddingProviderPort embeddingProvider(
            @Qualifier("openAiHttpClient") OpenAIClient openAi,
            AppProperties props,
            ResolveEmbeddingChoiceUseCase choice) {
        return new SelectedEmbeddingProvider(choice, chosen -> EmbeddingChoice.GEMINI.equals(chosen.provider())
                ? new GeminiEmbeddingClient(GeminiEmbeddingClient.DEFAULT_BASE_URL,
                        props.getGemini().getApiKey(), chosen.model())
                : new OpenAiAdapter(openAi, props, tierModel("openai", props.getAi().getEnrichmentTier(), props)));
    }

    /**
     * Enrichment's extraction side, which is where the volume is: one call per posting, and a
     * crawl brings thousands. It may run on the local CLI agent, so a flat-fee subscription can
     * absorb a backfill that would otherwise be billed per token.
     *
     * <p>Separate from {@link #embeddingProvider} because the two halves have different
     * constraints — this one only needs chat, and chat is the half a CLI agent can do.
     * {@code app.ai.enrichment-chat-provider} defaults to whatever enrichment-provider is set to,
     * so an install that has not thought about it keeps its current behaviour. Company grounding
     * uses it too, so an install whose only API key is for embeddings still researches companies.
     */
    @Bean("enrichmentChatProvider")
    public ChatProviderPort enrichmentChatProvider(
            @Qualifier("openAiHttpClient") OpenAIClient openAi,
            @Qualifier("geminiHttpClient") OpenAIClient gemini,
            AppProperties props) {
        return buildEnrichmentChatProvider(openAi, gemini, props, null);
    }

    /**
     * The same provider as {@link #enrichmentChatProvider}, for batched enrichment only: a CLI
     * agent here gets the batch timeout. Kept apart so company grounding, a user-facing call on
     * the shared bean, keeps the short shared timeout.
     */
    @Bean("enrichmentBatchChatProvider")
    public ChatProviderPort enrichmentBatchChatProvider(
            @Qualifier("openAiHttpClient") OpenAIClient openAi,
            @Qualifier("geminiHttpClient") OpenAIClient gemini,
            AppProperties props,
            @Value("${app.enrichment.batch.timeout:PT10M}") java.time.Duration batchTimeout) {
        return buildEnrichmentChatProvider(openAi, gemini, props, batchTimeout);
    }

    private static ChatProviderPort buildEnrichmentChatProvider(OpenAIClient openAi, OpenAIClient gemini,
                                                                AppProperties props,
                                                                java.time.Duration cliTimeout) {
        String provider = enrichmentChatProviderName(props);
        if (isCliProvider(provider)) {
            log.info("Enrichment extraction runs on the local CLI agent ({}); embeddings still "
                     + "use {}.", provider, props.getAi().getEnrichmentProvider());
            return new CliAgentAdapter(props, provider, cliTimeout);
        }
        String tier = props.getAi().getEnrichmentTier();
        return "gemini".equalsIgnoreCase(provider)
                ? new GeminiAdapter(gemini, props, tierModel("gemini", tier, props))
                : new OpenAiAdapter(openAi, props, tierModel("openai", tier, props));
    }

    /**
     * Generation runs on behalf of a signed-in user, so it is wrapped to log what each
     * call cost them. Enrichment is background work with nobody to bill and stays bare.
     */
    @Bean("generationAiProvider")
    public ChatProviderPort generationAiProvider(
            @Qualifier("openAiHttpClient") OpenAIClient openAi,
            @Qualifier("geminiHttpClient") OpenAIClient gemini,
            AppProperties props,
            AiUsageRepositoryPort usageRepo,
            CurrentUserPort currentUser,
            ManageAiCredentialUseCase credentials) {
        // Layers, outermost first: usage is logged for whatever ran, and what runs is
        // the user's own key when they have set one, else the server's provider — which
        // on a personal install is the local CLI agent.
        ChatProviderPort perUser = new PerUserChatProvider(
                buildGenerationProvider(openAi, gemini, props), credentials, currentUser, props);
        return new UsageRecordingChatProvider(perUser, usageRepo, currentUser);
    }

    private static ChatProviderPort buildGenerationProvider(OpenAIClient openAi,
                                                            OpenAIClient gemini,
                                                            AppProperties props) {
        String provider = props.getAi().getGenerationProvider();
        if (isCliProvider(provider)) {
            return new CliAgentAdapter(props, provider);
        }
        String tier = props.getAi().getGenerationTier();
        return "gemini".equalsIgnoreCase(provider)
                ? new GeminiAdapter(gemini, props, tierModel("gemini", tier, props))
                : new OpenAiAdapter(openAi, props, tierModel("openai", tier, props));
    }

    /** The provider enrichment extraction runs on: the chat override, else the enrichment one. */
    public static String enrichmentChatProviderName(AppProperties props) {
        return firstNonBlank(props.getAi().getEnrichmentChatProvider(), props.getAi().getEnrichmentProvider());
    }

    /** CLI-agent generation (Claude Code / Codex): the flat-fee path. Never used for embeddings. */
    public static boolean isCliProvider(String provider) {
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
            AppProperties.Openai o = props.getOpenai();
            String tierModel = switch (t) {
                case "economy" -> o.getEconomyModel();
                case "premium" -> o.getPremiumModel();
                default -> o.getStandardModel();
            };
            return firstNonBlank(tierModel, o.getModel());
        }
        AppProperties.Gemini g = props.getGemini();
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
