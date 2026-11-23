package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.document.PromptCategory;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void create_refuses_an_eleventh_template_of_one_kind() {
        when(repo.countOwned(userId, "COVER_LETTER"))
                .thenReturn((long) PromptTemplateService.MAX_OWN_TEMPLATES_PER_CATEGORY);

        assertThatThrownBy(() -> service.createTemplate(userId,
                categorised(userId, PromptCategory.COVER_LETTER, false)))
                .isInstanceOf(IllegalStateException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void duplicating_into_a_full_kind_is_refused() {
        when(repo.findById(templateId)).thenReturn(Optional.of(protectedTemplate()));
        when(repo.countOwned(userId, "COVER_LETTER"))
                .thenReturn((long) PromptTemplateService.MAX_OWN_TEMPLATES_PER_CATEGORY);

        assertThatThrownBy(() -> service.duplicate(templateId, userId, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void moving_a_template_into_a_full_kind_is_refused() {
        when(repo.findById(templateId))
                .thenReturn(Optional.of(categorised(userId, PromptCategory.GENERAL, false)));
        when(repo.countOwned(userId, "COVER_LETTER"))
                .thenReturn((long) PromptTemplateService.MAX_OWN_TEMPLATES_PER_CATEGORY);

        assertThatThrownBy(() -> service.updateTemplate(templateId, userId, false,
                categorised(userId, PromptCategory.COVER_LETTER, false)))
                .isInstanceOf(IllegalStateException.class);
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
                false, false, null);
        when(repo.findById(templateId)).thenReturn(Optional.of(original));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, userId, null);
        assertThat(copy.isPublic()).isFalse();
    }

    @Test
    void duplicate_does_not_carry_the_app_explainer() {
        when(repo.findById(templateId)).thenReturn(Optional.of(protectedTemplate()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, userId, null);
        assertThat(copy.explainerKey()).isNull();
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
        PromptTemplate original = new PromptTemplate(templateId, userId, "T",
                null, null, null, "prompt", null, true, null, 1, null, null, false, List.of(), 0,
                false, false, null);
        when(repo.findById(templateId)).thenReturn(Optional.of(original));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate copy = service.duplicate(templateId, anotherUser, null);
        assertThat(copy.userId()).isEqualTo(anotherUser);
    }

    // ── updateTemplate ────────────────────────────────────────────────────────

    @Test
    void an_update_takes_the_edited_content_but_keeps_what_the_editor_does_not_own() {
        PromptTemplate existing = new PromptTemplate(templateId, userId, "Old",
                PromptCategory.COVER_LETTER, null, "sys", "old prompt", null, false, null, 3,
                null, null, false, List.of("kept"), 7, false, true, null);
        PromptTemplate changes = new PromptTemplate(null, userId, "Renamed",
                PromptCategory.COVER_LETTER, null, "sys", "new prompt", null, false, null, 99,
                null, null, false, null, 0, true, false, null);
        when(repo.findById(templateId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptTemplate saved = service.updateTemplate(templateId, userId, false, changes);

        assertThat(saved.id()).isEqualTo(templateId);
        assertThat(saved.name()).isEqualTo("Renamed");
        assertThat(saved.userPrompt()).isEqualTo("new prompt");
        assertThat(saved.versionNumber()).isEqualTo(4);
        assertThat(saved.tags()).containsExactly("kept");
        assertThat(saved.usageCount()).isEqualTo(7);
        assertThat(saved.isProtected()).isFalse();
        assertThat(saved.isDefault()).isTrue();
    }

    // ── browsePublic ──────────────────────────────────────────────────────────

    @Test
    void browsing_shows_only_other_users_shared_templates() {
        PromptTemplate mine = template(UUID.randomUUID(), userId, "Mine", 1);
        PromptTemplate appOwned = template(UUID.randomUUID(), null, "App", 1);
        PromptTemplate theirs = template(UUID.randomUUID(), UUID.randomUUID(), "Theirs", 1);
        when(repo.findPublic()).thenReturn(List.of(mine, appOwned, theirs));

        assertThat(service.browsePublic(userId)).containsExactly(theirs);
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

    private PromptTemplate protectedTemplate() {
        return new PromptTemplate(templateId, null, "House prompt", PromptCategory.COVER_LETTER,
                null, "sys", "usr", null, true, null, 1, null, null, true, List.of(), 0,
                true, true, "prompts.explainers.house");
    }

    private PromptTemplate categorised(UUID owner, PromptCategory category, boolean isSystem) {
        return new PromptTemplate(templateId, owner, "T", category, null, "sys", "usr", null,
                false, null, 1, null, null, isSystem, List.of(), 0, isSystem, false, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PromptTemplate template(UUID id, UUID userId, String name, int version) {
        return new PromptTemplate(id, userId, name, null, null, null,
                "user prompt text", null, false, null, version, null, null, false, List.of(), 0,
                false, false, null);
    }
}
