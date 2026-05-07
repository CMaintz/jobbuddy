package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.CvVersion;

import java.util.List;
import java.util.UUID;

public interface GetCvVersionsUseCase {
    List<CvVersion> getCvVersions(UUID userId);
}
