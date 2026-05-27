package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.openai.client.OpenAIClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.util.List;

public class GeminiAdapter implements AiProviderPort {

    private final OpenAIClient client;
    private final AppProperties props;

    public GeminiAdapter(OpenAIClient client, AppProperties props) {
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
                .model(props.getGemini().getModel());

        if (composition.systemPrompt() != null && !composition.systemPrompt().isBlank()) {
            builder.addSystemMessage(composition.systemPrompt());
        }
        builder.addUserMessage(composition.resolvedFinalPrompt());

        if (jsonObject) {
            builder.responseFormat(ResponseFormatJsonObject.builder().build());
        }

        ChatCompletion completion = client.chat().completions().create(builder.build());
        return completion.choices().get(0).message().content().orElse("");
    }

    @Override
    public String embeddingModelName() {
        return props.getGemini().getEmbeddingModel();
    }

    @Override
    public String chatModelName() {
        return props.getGemini().getModel();
    }

    @Override
    public float[] embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(props.getGemini().getEmbeddingModel())
                .input(text)
                .build();
        CreateEmbeddingResponse response = client.embeddings().create(params);
        List<Float> values = response.data().get(0).embedding();
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
