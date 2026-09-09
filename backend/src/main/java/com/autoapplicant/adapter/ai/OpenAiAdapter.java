package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import java.util.List;

public class OpenAiAdapter implements AiProviderPort {

    private final OpenAIClient client;
    private final AppProperties props;
    /** Tier-resolved chat model; when set, overrides the provider's default {@code model}. */
    private final String modelOverride;

    public OpenAiAdapter(OpenAIClient client, AppProperties props) {
        this(client, props, null);
    }

    public OpenAiAdapter(OpenAIClient client, AppProperties props, String modelOverride) {
        this.client = client;
        this.props = props;
        this.modelOverride = modelOverride;
    }

    @Override
    public AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(resolveChatModel());

        if (composition.systemPrompt() != null && !composition.systemPrompt().isBlank()) {
            builder.addSystemMessage(composition.systemPrompt());
        }
        builder.addUserMessage(composition.resolvedFinalPrompt());

        if (jsonObject) {
            builder.responseFormat(ResponseFormatJsonObject.builder().build());
        }

        ChatCompletion completion = client.chat().completions().create(builder.build());
        String text = completion.choices().getFirst().message().content().orElse("");
        return completion.usage()
                .map(u -> new AiCompletion(text, (int) u.promptTokens(), (int) u.completionTokens(), resolveChatModel()))
                .orElseGet(() -> AiCompletion.untracked(text, resolveChatModel()));
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        // One request for the whole batch. The embeddings endpoint takes an array and returns
        // vectors in the same order, so a 200-posting batch is one round trip instead of 200.
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(resolveEmbeddingModel())
                .inputOfArrayOfStrings(texts)
                .build();
        CreateEmbeddingResponse response = client.embeddings().create(params);
        return response.data().stream().map(datum -> {
            List<Float> values = datum.embedding();
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) vector[i] = values.get(i);
            return vector;
        }).toList();
    }

    @Override
    public float[] embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(resolveEmbeddingModel())
                .input(text)
                .build();
        CreateEmbeddingResponse response = client.embeddings().create(params);
        List<Float> values = response.data().getFirst().embedding();
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private String resolveChatModel() {
        if (modelOverride != null && !modelOverride.isBlank()) return modelOverride;
        String configured = props.getOpenai().getModel();
        return configured != null && !configured.isBlank() ? configured : ChatModel.GPT_4O.toString();
    }

    @Override
    public String embeddingModelName() {
        return resolveEmbeddingModel();
    }

    @Override
    public String chatModelName() {
        return resolveChatModel();
    }

    private String resolveEmbeddingModel() {
        String configured = props.getOpenai().getEmbeddingModel();
        return configured != null && !configured.isBlank() ? configured : EmbeddingModel.TEXT_EMBEDDING_3_SMALL.toString();
    }
}
