package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenAiAdapter implements AiProviderPort {

    private final OpenAIClient client;
    private final AppProperties props;

    public OpenAiAdapter(OpenAIClient client, AppProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public String generate(PromptComposition composition) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O);

        if (composition.systemPrompt() != null && !composition.systemPrompt().isBlank()) {
            builder.addSystemMessage(composition.systemPrompt());
        }
        builder.addUserMessage(composition.resolvedFinalPrompt());

        ChatCompletion completion = client.chat().completions().create(builder.build());
        return completion.choices().get(0).message().content().orElse("");
    }

    @Override
    public float[] embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
                .input(text)
                .build();
        CreateEmbeddingResponse response = client.embeddings().create(params);
        List<Double> values = response.data().get(0).embedding();
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }
}
