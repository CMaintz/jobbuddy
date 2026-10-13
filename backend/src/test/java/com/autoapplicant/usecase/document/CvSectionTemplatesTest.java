package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.document.CvSection;
import com.autoapplicant.domain.document.CvSectionPrompts;
import com.autoapplicant.domain.document.PromptCategory;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CvSectionTemplatesTest {

    @Mock PromptTemplateRepositoryPort repo;

    private final UUID userId = UUID.randomUUID();

    @Test
    void anExplicitSectionTemplateWins() {
        PromptTemplate star = template(userId, PromptCategory.CV_EXPERIENCE, "STAR", false);
        when(repo.findById(star.id())).thenReturn(Optional.of(star));

        assertThat(templates().useForSection(userId, CvSection.EXPERIENCE, star.id())).contains(star);
    }

    @Test
    void anotherUsersPrivateTemplateFallsBackToTheDefault() {
        PromptTemplate theirs = template(UUID.randomUUID(), PromptCategory.CV_EXPERIENCE, "theirs", false);
        PromptTemplate fallback = template(null, PromptCategory.CV_EXPERIENCE, "default", false);
        when(repo.findById(theirs.id())).thenReturn(Optional.of(theirs));
        when(repo.findDefaultFor(userId, "CV_EXPERIENCE")).thenReturn(Optional.of(fallback));

        assertThat(templates().useForSection(userId, CvSection.EXPERIENCE, theirs.id())).contains(fallback);
    }

    @Test
    void aTemplateForAnotherKindIsNotAppliedToASection() {
        PromptTemplate letter = template(userId, PromptCategory.COVER_LETTER, "letter", false);
        PromptTemplate fallback = template(null, PromptCategory.CV_PROFILE, "default", false);
        when(repo.findById(letter.id())).thenReturn(Optional.of(letter));
        when(repo.findDefaultFor(userId, "CV_PROFILE")).thenReturn(Optional.of(fallback));

        assertThat(templates().useForSection(userId, CvSection.PROFILE, letter.id())).contains(fallback);
    }

    @Test
    void defaultsCollectEachSectionsChosenTemplate() {
        when(repo.findDefaultFor(eq(userId), anyString())).thenReturn(Optional.empty());
        when(repo.findDefaultFor(userId, "CV_EXPERIENCE")).thenReturn(
                Optional.of(template(null, PromptCategory.CV_EXPERIENCE, "Use XYZ.", false)));

        CvSectionPrompts prompts = templates().defaultsFor(userId);

        assertThat(prompts.forSection(CvSection.EXPERIENCE)).isEqualTo("Use XYZ.");
        assertThat(prompts.forSection(CvSection.PROFILE)).isNull();
    }

    private CvSectionTemplates templates() {
        return new CvSectionTemplates(repo);
    }

    private static PromptTemplate template(UUID owner, PromptCategory category, String body, boolean isPublic) {
        return new PromptTemplate(UUID.randomUUID(), owner, "T", category, null, null, body, null,
                isPublic, null, 1, null, null, owner == null, List.of(), 0, owner == null, false, null);
    }
}
