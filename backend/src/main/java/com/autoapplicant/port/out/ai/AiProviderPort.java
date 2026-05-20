package com.autoapplicant.port.out.ai;

import com.autoapplicant.domain.document.PromptComposition;

public interface AiProviderPort {
    String generate(PromptComposition composition);
    String generateJson(PromptComposition composition);
    float[] embed(String text);
}
