package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
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

    public OpenAiAdapter(OpenAIClient client, AppProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public String generate(PromptComposition composition) {
        return complete(composition, false);
    }

    @Override
    public String generateJson(PromptComposition composition) {
        return complete(composition, true);
    }

    private String complete(PromptComposition composition, boolean jsonObject) {
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
        return completion.choices().getFirst().message().content().orElse("");
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
