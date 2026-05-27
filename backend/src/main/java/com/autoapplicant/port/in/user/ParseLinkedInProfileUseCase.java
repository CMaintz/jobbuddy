package com.autoapplicant.port.in.user;

import java.util.UUID;

public interface ParseLinkedInProfileUseCase {
    String parseProfileFromText(UUID userId, String extractedText);
}
