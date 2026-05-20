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
                "max 300 words", true, null, 1, null, null, false);
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
                null, null, null, "prompt", null, true, null, 1, null, null, false);
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

    // ── helpers ───────────────────────────────────────────────────────────────

    private PromptTemplate template(UUID id, UUID userId, String name, int version) {
        return new PromptTemplate(id, userId, name, null, null, null,
                "user prompt text", null, false, null, version, null, null, false);
    }
}
