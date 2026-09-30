package com.autoapplicant.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.CustomSection;
import com.autoapplicant.domain.user.CustomSectionItem;
import com.autoapplicant.port.out.user.CustomSectionRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomSectionsServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    private final CustomSectionRepositoryPort repo = mock(CustomSectionRepositoryPort.class);
    private final CustomSectionsService service = new CustomSectionsService(repo);

    @Test
    void savingAssignsIdsToNewEntriesAndDropsBlanks() {
        when(repo.save(any(), any())).thenReturn(List.of());

        service.saveCustomSections(USER, List.of(
                new CustomSection(null, "Awards", List.of(
                        new CustomSectionItem(null, "Won the thing"),
                        new CustomSectionItem("keep-me", "  Existing id kept  "),
                        new CustomSectionItem(null, "   "))),
                new CustomSection(null, "  ", List.of(new CustomSectionItem(null, "orphan")))));

        ArgumentCaptor<List<CustomSection>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).save(eq(USER), captor.capture());
        List<CustomSection> saved = captor.getValue();

        // Blank-heading section dropped; only "Awards" survives.
        assertThat(saved).hasSize(1);
        CustomSection awards = saved.get(0);
        assertThat(awards.id()).isNotBlank();
        assertThat(awards.heading()).isEqualTo("Awards");
        // Blank item dropped; two survive; new one gets an id, existing id preserved; text trimmed.
        assertThat(awards.items()).hasSize(2);
        assertThat(awards.items().get(0).id()).isNotBlank();
        assertThat(awards.items().get(0).text()).isEqualTo("Won the thing");
        assertThat(awards.items().get(1).id()).isEqualTo("keep-me");
        assertThat(awards.items().get(1).text()).isEqualTo("Existing id kept");
    }
}
