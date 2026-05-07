package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessageParam;
import com.openai.models.ChatCompletionSystemMessageParam;
import com.openai.models.ChatCompletionUserMessageParam;
import com.openai.models.ChatModel;
import com.openai.models.CreateEmbeddingResponse;
import com.openai.models.EmbeddingCreateParams;
import com.openai.models.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        List<ChatCompletionMessageParam> messages = new ArrayList<>();

        if (composition.systemPrompt() != null && !composition.systemPrompt().isBlank()) {
            ChatCompletionSystemMessageParam system = ChatCompletionSystemMessageParam.builder()
                    .content(ChatCompletionSystemMessageParam.Content.ofTextContent(composition.systemPrompt()))
                    .role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                    .build();
            messages.add(ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(system));
        }

        ChatCompletionUserMessageParam user = ChatCompletionUserMessageParam.builder()
                .content(ChatCompletionUserMessageParam.Content.ofTextContent(composition.resolvedFinalPrompt()))
                .role(ChatCompletionUserMessageParam.Role.USER)
                .build();
        messages.add(ChatCompletionMessageParam.ofChatCompletionUserMessageParam(user));

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O)
                .messages(messages)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);
        return completion.choices().get(0).message().content().orElse("");
    }

    @Override
    public float[] embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
                .input(EmbeddingCreateParams.Input.ofString(text))
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
