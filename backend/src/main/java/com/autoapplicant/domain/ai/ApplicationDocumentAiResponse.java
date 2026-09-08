package com.autoapplicant.domain.ai;

import java.util.List;

public record ApplicationDocumentAiResponse(
        String body,
        List<String> notes
) {}
