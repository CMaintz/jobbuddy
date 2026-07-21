package com.autoapplicant.adapter.web.dto.document;

import java.util.List;
import java.util.UUID;

public record AnalyzeWritingStyleRequest(
        List<String> samples,
        List<UUID> documentIds
) {}
