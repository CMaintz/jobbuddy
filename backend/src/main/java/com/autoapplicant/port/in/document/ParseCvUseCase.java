package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.user.Profile;

import java.util.UUID;

public interface ParseCvUseCase {
    /**
     * Parses raw CV text (extracted from PDF/DOCX) via AI and returns
     * a structured Profile that the user can review and save.
     */
    Profile parseCvText(UUID userId, String rawCvText);
}
