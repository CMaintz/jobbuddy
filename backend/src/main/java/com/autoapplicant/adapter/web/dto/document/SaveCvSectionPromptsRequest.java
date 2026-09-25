package com.autoapplicant.adapter.web.dto.document;

import java.util.Map;

/** Section wire-key → the user's instruction. A blank or omitted value clears that section. */
public record SaveCvSectionPromptsRequest(Map<String, String> prompts) {}
