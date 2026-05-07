package com.autoapplicant.port.out.ai;

import com.autoapplicant.domain.document.PromptComposition;

public interface AiProviderPort {
    String generate(PromptComposition composition);
    float[] embed(String text);
}
