package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.CvSection;
import com.autoapplicant.domain.document.CvSectionPrompts;

import java.util.Map;
import java.util.UUID;

public interface ManageCvSectionPromptsUseCase {

    CvSectionPrompts get(UUID userId);

    CvSectionPrompts save(UUID userId, Map<CvSection, String> prompts);
}
