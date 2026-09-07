package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.CvVersion;

import java.util.UUID;

public interface UploadCvUseCase {
    CvVersion uploadCv(UUID userId, String name, String content, String format);
}
