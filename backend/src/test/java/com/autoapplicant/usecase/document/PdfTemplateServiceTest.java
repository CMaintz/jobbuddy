package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.domain.document.PdfTemplate;
import com.autoapplicant.port.out.document.PdfTemplateRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PdfTemplateServiceTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    private final PdfTemplateRepositoryPort repo = mock(PdfTemplateRepositoryPort.class);
    private final PdfTemplateService service = new PdfTemplateService(repo);

    private static PdfTemplate template(UUID owner, boolean system) {
        return new PdfTemplate(ID, owner, "T", null, "CV", "<html/>", "", system, true, null);
    }

    @Test
    void another_users_template_reads_as_absent() {
        when(repo.findById(ID)).thenReturn(Optional.of(template(ALICE, false)));

        assertThat(service.getById(ID, BOB)).isEmpty();
        assertThat(service.getById(ID, ALICE)).isPresent();
    }

    @Test
    void system_templates_are_readable_by_everyone() {
        when(repo.findById(ID)).thenReturn(Optional.of(template(null, true)));

        assertThat(service.getById(ID, BOB)).isPresent();
    }

    @Test
    void another_users_template_cannot_be_overwritten() {
        when(repo.findById(ID)).thenReturn(Optional.of(template(ALICE, false)));

        assertThatThrownBy(() -> service.update(template(BOB, false)))
                .isInstanceOf(NotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void the_owner_can_update_their_template() {
        when(repo.findById(ID)).thenReturn(Optional.of(template(ALICE, false)));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(template(ALICE, false)).userId()).isEqualTo(ALICE);
    }

    @Test
    void another_users_template_is_not_deleted() {
        when(repo.findById(ID)).thenReturn(Optional.of(template(ALICE, false)));

        service.delete(ID, BOB);

        verify(repo, never()).deleteById(any());
    }
}
