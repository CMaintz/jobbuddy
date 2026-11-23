package com.autoapplicant.domain.ai;

import java.util.List;

/**
 * The model's answer to a document prompt.
 *
 * @param needs for a prose letter, the employer needs the letter is written from; absent otherwise
 */
public record ApplicationDocumentAiResponse(
        String body,
        List<String> notes,
        List<String> needs
) {}
