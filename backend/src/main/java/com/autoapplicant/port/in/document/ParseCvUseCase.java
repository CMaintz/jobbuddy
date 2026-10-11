package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.user.Profile;
import java.util.UUID;

public interface ParseCvUseCase {
    /**
     * Parses raw CV text (extracted from PDF/DOCX) via AI and returns
     * a structured Profile that the user can review and save.
     *
     * @throws com.autoapplicant.domain.common.CvParseException when the text is blank or the
     *     AI parse fails; the profile is left untouched
     */
    Profile parseCvText(UUID userId, String rawCvText);
}
