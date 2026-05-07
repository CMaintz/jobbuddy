package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiConfig {

    @Bean
    public OpenAIClient openAIClient(AppProperties props) {
        return OpenAIOkHttpClient.builder()
                .apiKey(props.getOpenai().getApiKey())
                .build();
    }
}
