package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.Education;

import java.util.List;
import java.util.UUID;

public interface ManageEducationUseCase {
    Education addEducation(UUID userId, Education education);
    Education updateEducation(UUID userId, UUID id, Education education);
    List<Education> getEducation(UUID userId);
    void deleteEducation(UUID userId, UUID id);
}
