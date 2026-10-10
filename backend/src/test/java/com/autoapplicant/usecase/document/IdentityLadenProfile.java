package com.autoapplicant.usecase.document;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.Certification;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.domain.user.Project;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.CareerTargetRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.CustomSectionRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.port.out.user.InterviewStoryRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.ProfileStrengthRepositoryPort;
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import com.autoapplicant.port.out.user.SpokenLanguageRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A user whose profile carries every identity-bearing value we store: name, both emails, phone,
 * photo, social links, and the structured URLs on projects and certifications. Shared by the
 * privacy tests so they all plant the same values and assert the same absence.
 */
final class IdentityLadenProfile {

    static final UUID USER_ID = UUID.randomUUID();
    static final UUID PROJECT_ID = UUID.randomUUID();
    static final UUID CERT_ID = UUID.randomUUID();

    static final String FULL_NAME = "Freja Testesen";
    static final String LOGIN_EMAIL = "freja.login@example.dk";
    static final String CONTACT_EMAIL = "freja.testesen@example.dk";
    static final String PHONE = "+45 12 34 56 78";
    static final String PHOTO_URL = "https://cdn.example.dk/photos/freja.jpg";
    static final String LINKEDIN_URL = "https://www.linkedin.com/in/freja-testesen";
    static final String GITHUB_PROFILE_URL = "https://github.com/frejatestesen";
    static final String WEBSITE_URL = "https://freja.example.dk";
    static final String GITHUB_HANDLE = "frejatestesen";
    static final String PROJECT_GITHUB_URL = "https://github.com/frejatestesen/ledger";
    static final String PROJECT_LIVE_URL = "https://ledger.freja-demo.dk";
    static final String CREDENTIAL_URL = "https://www.credly.com/badges/freja-0042";

    static final String PROJECT_NAME = "Ledger reconciliation engine";
    static final String CERT_NAME = "AWS Solutions Architect";

    /** Every value that must never reach the AI provider. */
    static final List<String> IDENTITY_VALUES = List.of(
            FULL_NAME, LOGIN_EMAIL, CONTACT_EMAIL, PHONE, PHOTO_URL, LINKEDIN_URL,
            GITHUB_PROFILE_URL, WEBSITE_URL, GITHUB_HANDLE, PROJECT_GITHUB_URL, PROJECT_LIVE_URL,
            CREDENTIAL_URL);

    private IdentityLadenProfile() {}

    static Profile profile() {
        return new Profile(UUID.randomUUID(), USER_ID, "Backend engineer", "Builds payment systems.",
                7, List.of("Java"), List.of("Cycling"), null, null, null, null, null,
                Instant.now(), Instant.now());
    }

    static User user() {
        return new User(USER_ID, LOGIN_EMAIL, null, null, null, UserRole.USER, true, true,
                Instant.now(), Instant.now());
    }

    static ProfilePrivateInfo privateInfo() {
        return new ProfilePrivateInfo(UUID.randomUUID(), USER_ID, FULL_NAME, PHONE, PHOTO_URL,
                "Copenhagen", "Kobenhavn", CONTACT_EMAIL, Instant.now(), Instant.now());
    }

    static List<ProfileSocial> socials() {
        return List.of(social("LinkedIn", LINKEDIN_URL, "linkedin"),
                social("GitHub", GITHUB_PROFILE_URL, "github"),
                social("Website", WEBSITE_URL, "globe"));
    }

    static Project project() {
        return new Project(PROJECT_ID, USER_ID, PROJECT_NAME, "Matches bank lines to invoices.",
                List.of("Java", "Kafka"), PROJECT_GITHUB_URL, PROJECT_LIVE_URL, null,
                "Cut manual matching by 80%", null, LocalDate.of(2024, 1, 1), null, true, 0,
                Instant.now(), Instant.now(), List.of());
    }

    static Certification certification() {
        return new Certification(CERT_ID, USER_ID, CERT_NAME, "Amazon", LocalDate.of(2023, 5, 1),
                null, CREDENTIAL_URL, Instant.now());
    }

    /** A real context service over mocked ports that hold this user's profile, project and cert. */
    static CareerProfileContextService contextService() {
        ProfileRepositoryPort profileRepo = mock(ProfileRepositoryPort.class);
        ProjectRepositoryPort projectRepo = mock(ProjectRepositoryPort.class);
        CertificationRepositoryPort certRepo = mock(CertificationRepositoryPort.class);
        when(profileRepo.findByUserId(USER_ID)).thenReturn(Optional.of(profile()));
        when(projectRepo.findByUserId(USER_ID)).thenReturn(List.of(project()));
        when(certRepo.findByUserId(USER_ID)).thenReturn(List.of(certification()));
        return new CareerProfileContextService(profileRepo, mock(WorkExperienceRepositoryPort.class),
                projectRepo, mock(EducationRepositoryPort.class), certRepo,
                mock(ProfileSkillRepositoryPort.class), mock(SkillTaxonomyRepositoryPort.class),
                mock(SpokenLanguageRepositoryPort.class), mock(ProfileStrengthRepositoryPort.class),
                mock(CareerTargetRepositoryPort.class), mock(InterviewStoryRepositoryPort.class),
                mock(CustomSectionRepositoryPort.class), new ObjectMapper());
    }

    private static ProfileSocial social(String platform, String url, String iconKey) {
        return new ProfileSocial(UUID.randomUUID(), USER_ID, platform, url, null, iconKey, 0,
                Instant.now(), Instant.now());
    }
}
