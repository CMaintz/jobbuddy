package com.autoapplicant.usecase.user;

import com.autoapplicant.port.in.user.ExportUserDataUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import com.autoapplicant.port.out.user.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserDataExportService implements ExportUserDataUseCase {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final PreferencesRepositoryPort prefsRepo;
    private final WorkExperienceRepositoryPort workExpRepo;
    private final ProjectRepositoryPort projectRepo;
    private final CertificationRepositoryPort certRepo;
    private final ApplicationRepositoryPort appRepo;
    private final RecommendationFeedbackRepositoryPort feedbackRepo;

    public UserDataExportService(UserRepositoryPort userRepo,
                                  ProfileRepositoryPort profileRepo,
                                  PreferencesRepositoryPort prefsRepo,
                                  WorkExperienceRepositoryPort workExpRepo,
                                  ProjectRepositoryPort projectRepo,
                                  CertificationRepositoryPort certRepo,
                                  ApplicationRepositoryPort appRepo,
                                  RecommendationFeedbackRepositoryPort feedbackRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.prefsRepo = prefsRepo;
        this.workExpRepo = workExpRepo;
        this.projectRepo = projectRepo;
        this.certRepo = certRepo;
        this.appRepo = appRepo;
        this.feedbackRepo = feedbackRepo;
    }

    @Override
    public Map<String, Object> exportUserData(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("user", userRepo.findById(userId).orElse(null));
        data.put("profile", profileRepo.findByUserId(userId).orElse(null));
        data.put("preferences", prefsRepo.findByUserId(userId).orElse(null));
        data.put("workExperience", workExpRepo.findByUserId(userId));
        data.put("projects", projectRepo.findByUserId(userId));
        data.put("certifications", certRepo.findByUserId(userId));
        data.put("applications", appRepo.findByUserId(userId));
        data.put("recommendationFeedback", feedbackRepo.findByUserId(userId));

        return data;
    }
}
