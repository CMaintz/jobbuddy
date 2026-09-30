package com.autoapplicant.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.user.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * User B knows the id of one of user A's profile rows. Updating it must not overwrite or take
 * over A's row; it has to look exactly like a row that does not exist.
 */
class ProfileSectionOwnershipTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID ROW = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2025-01-01T00:00:00Z");

    @Nested
    class WorkExperienceRows {
        final WorkExperienceRepositoryPort repo = mock(WorkExperienceRepositoryPort.class);
        final WorkExperienceService service = new WorkExperienceService(repo);

        WorkExperience row(UUID owner, String company) {
            return new WorkExperience(ROW, owner, company, "Dev", null, null, null, null, false,
                    List.of(), List.of(), 0, CREATED, null, List.of());
        }

        @Test
        void another_users_row_cannot_be_updated() {
            when(repo.findById(ROW)).thenReturn(Optional.of(row(ALICE, "Acme")));

            assertThatThrownBy(() -> service.updateWorkExperience(BOB, ROW, row(BOB, "Hijacked")))
                    .isInstanceOf(NotFoundException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void an_unknown_id_is_not_created_by_an_update() {
            when(repo.findById(ROW)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateWorkExperience(BOB, ROW, row(BOB, "New")))
                    .isInstanceOf(NotFoundException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void the_owner_can_update_and_keeps_the_original_created_at() {
            when(repo.findById(ROW)).thenReturn(Optional.of(row(ALICE, "Acme")));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkExperience saved = service.updateWorkExperience(ALICE, ROW, row(null, "Acme 2"));

            assertThat(saved.userId()).isEqualTo(ALICE);
            assertThat(saved.companyName()).isEqualTo("Acme 2");
            assertThat(saved.createdAt()).isEqualTo(CREATED);
        }

        @Test
        void delete_is_scoped_to_the_caller() {
            service.deleteWorkExperience(BOB, ROW);
            verify(repo).deleteByIdAndUserId(ROW, BOB);
        }
    }

    @Nested
    class ProjectRows {
        final ProjectRepositoryPort repo = mock(ProjectRepositoryPort.class);
        final ProjectService service = new ProjectService(repo);

        Project row(UUID owner, String name) {
            return new Project(ROW, owner, name, null, List.of(), null, null, null, null, null,
                    null, null, false, 0, CREATED, null, List.of());
        }

        @Test
        void another_users_row_cannot_be_updated() {
            when(repo.findById(ROW)).thenReturn(Optional.of(row(ALICE, "Mine")));

            assertThatThrownBy(() -> service.updateProject(BOB, ROW, row(BOB, "Hijacked")))
                    .isInstanceOf(NotFoundException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void the_owner_can_update() {
            when(repo.findById(ROW)).thenReturn(Optional.of(row(ALICE, "Mine")));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.updateProject(ALICE, ROW, row(null, "Renamed")).name()).isEqualTo("Renamed");
        }

        @Test
        void delete_is_scoped_to_the_caller() {
            service.deleteProject(BOB, ROW);
            verify(repo).deleteByIdAndUserId(ROW, BOB);
        }
    }

    @Nested
    class EducationRows {
        final EducationRepositoryPort repo = mock(EducationRepositoryPort.class);
        final EducationService service = new EducationService(repo);

        Education row(UUID owner, String institution) {
            return new Education(ROW, owner, institution, "BSc", null, null, null, null, null, 0,
                    CREATED, null, List.of());
        }

        @Test
        void another_users_row_cannot_be_updated() {
            when(repo.findById(ROW)).thenReturn(Optional.of(row(ALICE, "DTU")));

            assertThatThrownBy(() -> service.updateEducation(BOB, ROW, row(BOB, "Hijacked")))
                    .isInstanceOf(NotFoundException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void the_owner_can_update() {
            when(repo.findById(ROW)).thenReturn(Optional.of(row(ALICE, "DTU")));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.updateEducation(ALICE, ROW, row(null, "KU")).institution()).isEqualTo("KU");
        }

        @Test
        void delete_is_scoped_to_the_caller() {
            service.deleteEducation(BOB, ROW);
            verify(repo).deleteByIdAndUserId(ROW, BOB);
        }
    }

    @Nested
    class SocialRows {
        final ProfileSocialRepositoryPort repo = mock(ProfileSocialRepositoryPort.class);
        final ProfileSocialService service = new ProfileSocialService(repo);

        ProfileSocial row(UUID owner, String url) {
            return new ProfileSocial(ROW, owner, "github", url, null, null, 0, CREATED, null);
        }

        @Test
        void another_users_row_cannot_be_updated() {
            when(repo.findByIdAndUserId(ROW, BOB)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateSocial(BOB, ROW, row(BOB, "https://evil")))
                    .isInstanceOf(NotFoundException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void the_owner_can_update() {
            when(repo.findByIdAndUserId(ROW, ALICE)).thenReturn(Optional.of(row(ALICE, "https://a")));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.updateSocial(ALICE, ROW, row(null, "https://b")).url()).isEqualTo("https://b");
        }
    }

    @Nested
    class StrengthRows {
        final ProfileStrengthRepositoryPort repo = mock(ProfileStrengthRepositoryPort.class);
        final ProfileStrengthService service = new ProfileStrengthService(repo);

        ProfileStrength row(UUID owner, String title) {
            return new ProfileStrength(ROW, owner, title, null, null, 0, CREATED, null);
        }

        @Test
        void another_users_row_cannot_be_updated() {
            when(repo.findByIdAndUserId(ROW, BOB)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateStrength(BOB, ROW, row(BOB, "Hijacked")))
                    .isInstanceOf(NotFoundException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void the_owner_can_update() {
            when(repo.findByIdAndUserId(ROW, ALICE)).thenReturn(Optional.of(row(ALICE, "Old")));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.updateStrength(ALICE, ROW, row(null, "New")).title()).isEqualTo("New");
        }
    }

    @Nested
    class LanguageRows {
        final SpokenLanguageRepositoryPort repo = mock(SpokenLanguageRepositoryPort.class);
        final SpokenLanguageService service = new SpokenLanguageService(repo);

        SpokenLanguage row(UUID id, UUID owner) {
            return new SpokenLanguage(id, owner, "Danish", LanguageProficiency.NATIVE, 0, null, null);
        }

        @Test
        void another_users_row_cannot_be_updated() {
            when(repo.existsByIdAndUserId(ROW, BOB)).thenReturn(false);

            assertThatThrownBy(() -> service.save(row(ROW, BOB)))
                    .isInstanceOf(NotFoundException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void the_owner_can_update() {
            when(repo.existsByIdAndUserId(ROW, ALICE)).thenReturn(true);
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.save(row(ROW, ALICE)).userId()).isEqualTo(ALICE);
        }

        @Test
        void a_new_language_needs_no_ownership_check() {
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.save(row(null, BOB));

            verify(repo, never()).existsByIdAndUserId(any(), any());
        }
    }
}
