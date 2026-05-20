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
import com.openai.models.ResponseFormatJsonObject;
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
        return complete(composition, false);
    }

    @Override
    public String generateJson(PromptComposition composition) {
        return complete(composition, true);
    }

    private String complete(PromptComposition composition, boolean jsonObject) {
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

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(resolveChatModel())
                .messages(messages);

        if (jsonObject) {
            builder.responseFormat(ResponseFormatJsonObject.builder()
                        .type(ResponseFormatJsonObject.Type.JSON_OBJECT)
                        .build());
        }

        ChatCompletionCreateParams params = builder.build();

        ChatCompletion completion = client.chat().completions().create(params);
        return completion.choices().get(0).message().content().orElse("");
    }

    @Override
    public float[] embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(resolveEmbeddingModel())
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

    private String resolveChatModel() {
        String configured = props.getOpenai().getModel();
        return configured != null && !configured.isBlank() ? configured : ChatModel.GPT_4O.toString();
    }

    private String resolveEmbeddingModel() {
        String configured = props.getOpenai().getEmbeddingModel();
        return configured != null && !configured.isBlank() ? configured : EmbeddingModel.TEXT_EMBEDDING_3_SMALL.toString();
    }
}
