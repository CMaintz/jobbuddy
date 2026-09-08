package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptCategory;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptTemplateServiceTest {

    @Mock PromptTemplateRepositoryPort repo;

    PromptTemplateService service;

    UUID userId     = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PromptTemplateService(repo);
    }

    // ── createTemplate ────────────────────────────────────────────────────────

    @Test
    void create_sets_version_number_to_1() {
        PromptTemplate incoming = template(null, userId, "My Template", 99);
        PromptTemplate saved    = template(templateId, userId, "My Template", 1);
        when(repo.save(any())).thenReturn(saved);

        service.createTemplate(userId, incoming);

        ArgumentCaptor<PromptTemplate> cap = ArgumentCaptor.forClass(PromptTemplate.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().versionNumber()).isEqualTo(1);
    }

    @Test
    void create_sets_null_id_for_repo_to_assign() {
        PromptTemplate incoming = template(templateId, userId, "My Template", 1);
        when(repo.save(any())).thenReturn(incoming);

        service.createTemplate(userId, incoming);

        ArgumentCaptor<PromptTemplate> cap = ArgumentCaptor.forClass(PromptTemplate.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().id()).isNull();
    }

    @Test
    void create_preserves_name_and_user_prompt() {
        PromptTemplate incoming = new PromptTemplate(null, userId, "Cover Letter",
                PromptCategory.COVER_LETTER, "desc", "system", "Write a cover letter for {job}",
                "max 300 words", true, null, 1, null, null, false, List.of(), 0, false, false);
        when(repo.save(any())).thenReturn(incoming);

        service.createTemplate(userId, incoming);

        ArgumentCaptor<PromptTemplate> cap = ArgumentCaptor.forClass(PromptTemplate.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().name()).isEqualTo("Cover Letter");
        assertThat(cap.getValue().userPrompt()).isEqualTo("Write a cover letter for {job}");
        assertThat(cap.getValue().userId()).isEqualTo(userId);
    }

    @Test
    void create_returns_saved_template() {
        PromptTemplate saved = template(templateId, userId, "T", 1);
        when(repo.save(any())).thenReturn(saved);
        assertThat(service.createTemplate(userId, template(null, userId, "T", 1))).isSameAs(saved);
    }

    // ── getTemplates ──────────────────────────────────────────────────────────

    @Test
    void get_templates_delegates_to_repo() {
        List<PromptTemplate> list = List.of(template(templateId, userId, "T", 1));
        when(repo.findByUserId(userId)).thenReturn(list);
        assertThat(service.getTemplates(userId)).isSameAs(list);
    }

    // ── duplicate ─────────────────────────────────────────────────────────────

    @Test
    void duplicate_increments_version_number() {
        PromptTemplate original = template(templateId, userId, "Original", 2);
        when(repo.findById(templateId)).thenReturn(Optional.of(original));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, userId, null);
        assertThat(copy.versionNumber()).isEqualTo(3);
    }

    @Test
    void duplicate_sets_parent_template_id() {
        PromptTemplate original = template(templateId, userId, "Original", 1);
        when(repo.findById(templateId)).thenReturn(Optional.of(original));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, userId, null);
        assertThat(copy.parentTemplateId()).isEqualTo(templateId);
    }

    @Test
    void duplicate_appends_copy_suffix_when_no_name_given() {
        PromptTemplate original = template(templateId, userId, "My Template", 1);
        when(repo.findById(templateId)).thenReturn(Optional.of(original));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, userId, null);
        assertThat(copy.name()).isEqualTo("My Template (copy)");
    }

    @Test
    void duplicate_uses_provided_name_when_given() {
        PromptTemplate original = template(templateId, userId, "My Template", 1);
        when(repo.findById(templateId)).thenReturn(Optional.of(original));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, userId, "Custom Name");
        assertThat(copy.name()).isEqualTo("Custom Name");
    }

    @Test
    void duplicate_sets_is_public_to_false() {
        PromptTemplate original = new PromptTemplate(templateId, userId, "Public T",
                null, null, null, "prompt", null, true, null, 1, null, null, false, List.of(), 0,
                false, false);
        when(repo.findById(templateId)).thenReturn(Optional.of(original));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, userId, null);
        assertThat(copy.isPublic()).isFalse();
    }

    @Test
    void duplicate_throws_when_template_not_found() {
        when(repo.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicate(templateId, userId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void duplicate_assigns_requesting_user_id() {
        UUID anotherUser = UUID.randomUUID();
        PromptTemplate original = template(templateId, userId, "T", 1);
        when(repo.findById(templateId)).thenReturn(Optional.of(original));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, anotherUser, null);
        assertThat(copy.userId()).isEqualTo(anotherUser);
    }


    // ── protection vs default ─────────────────────────────────────────────────
    //
    // Two properties that used to be one flag. A protected prompt ships with the app: it is
    // duplicable but never editable or deletable, so customising it means editing your own copy.
    // Which prompt a category actually uses is a separate, per-user choice that never writes to
    // the app's content — so resetting always has something to fall back to.

    @Test
    void a_protected_template_cannot_be_edited_and_the_error_says_what_to_do_instead() {
        when(repo.findById(templateId)).thenReturn(Optional.of(protectedTemplate()));

        assertThatThrownBy(() -> service.updateTemplate(templateId, userId, false, protectedTemplate()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Duplicate it");
    }

    @Test
    void a_protected_template_cannot_be_deleted() {
        when(repo.findById(templateId)).thenReturn(Optional.of(protectedTemplate()));

        assertThatThrownBy(() -> service.deleteTemplate(templateId, userId, false))
                .isInstanceOf(SecurityException.class);
        verify(repo, never()).deleteById(any());
    }

    @Test
    void duplicating_a_protected_template_produces_an_unprotected_copy_the_user_owns() {
        when(repo.findById(templateId)).thenReturn(Optional.of(protectedTemplate()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, userId, "Mine");

        assertThat(copy.isProtected()).isFalse();
        assertThat(copy.isDefault()).isFalse();
        assertThat(copy.userId()).isEqualTo(userId);
    }

    @Test
    void a_users_own_template_is_never_protected_or_the_app_default() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate created = service.createTemplate(userId,
                template(null, userId, "Mine", 1));

        assertThat(created.isProtected()).isFalse();
        assertThat(created.isDefault()).isFalse();
    }

    @Test
    void selecting_a_default_records_the_users_choice_rather_than_editing_the_template() {
        PromptTemplate mine = categorised(userId, PromptCategory.COVER_LETTER, false);
        when(repo.findById(templateId)).thenReturn(Optional.of(mine));

        service.selectDefault(userId, PromptCategory.COVER_LETTER, templateId);

        verify(repo).setUserDefault(userId, "COVER_LETTER", templateId);
        verify(repo, never()).save(any());
    }

    @Test
    void a_template_cannot_be_made_the_default_for_a_category_it_is_not_in() {
        when(repo.findById(templateId)).thenReturn(Optional.of(
                categorised(userId, PromptCategory.COVER_LETTER, false)));

        assertThatThrownBy(() -> service.selectDefault(userId, PromptCategory.CV_TAILORING, templateId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void someone_elses_private_template_cannot_be_made_your_default() {
        PromptTemplate theirs = categorised(UUID.randomUUID(), PromptCategory.COVER_LETTER, false);
        when(repo.findById(templateId)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.selectDefault(userId, PromptCategory.COVER_LETTER, templateId))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void resetting_a_default_drops_the_users_choice_so_the_apps_prompt_comes_back() {
        service.resetDefault(userId, PromptCategory.COVER_LETTER);

        verify(repo).clearUserDefault(userId, "COVER_LETTER");
    }

    private PromptTemplate protectedTemplate() {
        return new PromptTemplate(templateId, null, "House prompt", PromptCategory.COVER_LETTER,
                null, "sys", "usr", null, true, null, 1, null, null, true, List.of(), 0,
                true, true);
    }

    private PromptTemplate categorised(UUID owner, PromptCategory category, boolean isSystem) {
        return new PromptTemplate(templateId, owner, "T", category, null, "sys", "usr", null,
                false, null, 1, null, null, isSystem, List.of(), 0, isSystem, false);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PromptTemplate template(UUID id, UUID userId, String name, int version) {
        return new PromptTemplate(id, userId, name, null, null, null,
                "user prompt text", null, false, null, version, null, null, false, List.of(), 0,
                false, false);
    }
}
