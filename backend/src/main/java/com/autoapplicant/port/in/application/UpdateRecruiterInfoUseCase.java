package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;

import java.util.UUID;

public interface UpdateRecruiterInfoUseCase {
    Application updateRecruiterInfo(UUID applicationId, UUID userId,
                                    String recruiterName, String recruiterEmail,
                                    String recruiterMessage, String recruiterReply);
}
