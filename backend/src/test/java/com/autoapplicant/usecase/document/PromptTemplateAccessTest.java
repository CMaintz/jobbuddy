package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.document.PromptCategory;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Who may change, copy, favourite or default to a prompt template. A protected prompt ships with
 * the app: everyone can duplicate it, only an admin can change it. Everything else belongs to its
 * creator. Which prompt a category uses is a per-user choice that never writes to the template.
 */
@ExtendWith(MockitoExtension.class)
class PromptTemplateAccessTest {

    @Mock PromptTemplateRepositoryPort repo;

    PromptTemplateService service;

    UUID userId = UUID.randomUUID();
    UUID otherUser = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PromptTemplateService(repo);
    }

    @Test
    void another_users_private_template_cannot_be_duplicated() {
        when(repo.findById(templateId)).thenReturn(Optional.of(template(templateId, userId, "Mine", 1)));

        assertThatThrownBy(() -> service.duplicate(templateId, otherUser, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void another_users_private_template_cannot_be_favourited() {
        when(repo.findById(templateId)).thenReturn(Optional.of(template(templateId, userId, "Mine", 1)));

        assertThatThrownBy(() -> service.favourite(otherUser, templateId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).addFavourite(any(), any());
    }

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
    void someone_elses_template_cannot_be_updated() {
        when(repo.findById(templateId))
                .thenReturn(Optional.of(categorised(userId, PromptCategory.COVER_LETTER)));

        assertThatThrownBy(() -> service.updateTemplate(templateId, otherUser, false,
                categorised(otherUser, PromptCategory.COVER_LETTER)))
                .isInstanceOf(SecurityException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void someone_elses_template_cannot_be_deleted() {
        when(repo.findById(templateId))
                .thenReturn(Optional.of(categorised(userId, PromptCategory.COVER_LETTER)));

        assertThatThrownBy(() -> service.deleteTemplate(templateId, otherUser, false))
                .isInstanceOf(SecurityException.class);
        verify(repo, never()).deleteById(any());
    }

    @Test
    void an_admin_can_delete_a_protected_template() {
        when(repo.findById(templateId)).thenReturn(Optional.of(protectedTemplate()));

        service.deleteTemplate(templateId, userId, true);

        verify(repo).deleteById(templateId);
    }

    @Test
    void an_admin_can_edit_a_protected_template() {
        when(repo.findById(templateId)).thenReturn(Optional.of(protectedTemplate()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate saved = service.updateTemplate(templateId, userId, true,
                categorised(userId, PromptCategory.COVER_LETTER));

        assertThat(saved.userPrompt()).isEqualTo("usr");
        assertThat(saved.isProtected()).isTrue();
    }

    @Test
    void favouriting_a_missing_template_is_refused() {
        when(repo.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.favourite(userId, templateId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).addFavourite(any(), any());
    }

    @Test
    void selecting_a_default_records_the_users_choice_rather_than_editing_the_template() {
        PromptTemplate mine = categorised(userId, PromptCategory.COVER_LETTER);
        when(repo.findById(templateId)).thenReturn(Optional.of(mine));

        service.selectDefault(userId, PromptCategory.COVER_LETTER, templateId);

        verify(repo).setUserDefault(userId, "COVER_LETTER", templateId);
        verify(repo, never()).save(any());
    }

    @Test
    void a_template_cannot_be_made_the_default_for_a_category_it_is_not_in() {
        when(repo.findById(templateId)).thenReturn(Optional.of(
                categorised(userId, PromptCategory.COVER_LETTER)));

        assertThatThrownBy(() -> service.selectDefault(userId, PromptCategory.CV_TAILORING, templateId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void someone_elses_private_template_cannot_be_made_your_default() {
        PromptTemplate theirs = categorised(UUID.randomUUID(), PromptCategory.COVER_LETTER);
        when(repo.findById(templateId)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.selectDefault(userId, PromptCategory.COVER_LETTER, templateId))
                .isInstanceOf(SecurityException.class);
    }

    private PromptTemplate protectedTemplate() {
        return new PromptTemplate(templateId, null, "House prompt", PromptCategory.COVER_LETTER,
                null, "house sys", "house usr", null, true, null, 1, null, null, true, List.of(), 0,
                true, true, "prompts.explainers.house");
    }

    private PromptTemplate categorised(UUID owner, PromptCategory category) {
        return new PromptTemplate(templateId, owner, "T", category, null, "sys", "usr", null,
                false, null, 1, null, null, false, List.of(), 0, false, false, null);
    }

    private PromptTemplate template(UUID id, UUID owner, String name, int version) {
        return new PromptTemplate(id, owner, name, null, null, null,
                "user prompt text", null, false, null, version, null, null, false, List.of(), 0,
                false, false, null);
    }
}
