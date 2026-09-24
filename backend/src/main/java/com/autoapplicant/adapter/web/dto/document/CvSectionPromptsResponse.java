package com.autoapplicant.adapter.web.dto.document;

import com.autoapplicant.domain.document.CvSectionPrompts;
import java.util.LinkedHashMap;
import java.util.Map;

/** The user's saved section prompts, keyed by section wire-key. */
public record CvSectionPromptsResponse(Map<String, String> prompts) {

    public static CvSectionPromptsResponse from(CvSectionPrompts domain) {
        Map<String, String> out = new LinkedHashMap<>();
        domain.prompts().forEach((section, value) -> out.put(section.key(), value));
        return new CvSectionPromptsResponse(out);
    }
}
