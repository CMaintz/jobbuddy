package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.CvVersion;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvServiceTest {

    @Mock CvVersionRepositoryPort repo;

    CvService service;
    UUID userId = UUID.randomUUID();
    UUID cvId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CvService(repo);
    }

    // ── uploadCv ──────────────────────────────────────────────────────────────

    @Test
    void upload_defaults_format_to_markdown_when_null() {
        CvVersion saved = cv(cvId, userId, "Resume", "MARKDOWN");
        when(repo.save(any())).thenReturn(saved);

        service.uploadCv(userId, "Resume", "content", null);

        ArgumentCaptor<CvVersion> cap = ArgumentCaptor.forClass(CvVersion.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().format()).isEqualTo("MARKDOWN");
    }

    @Test
    void upload_uses_provided_format() {
        CvVersion saved = cv(cvId, userId, "Resume", "PDF");
        when(repo.save(any())).thenReturn(saved);

        service.uploadCv(userId, "Resume", "content", "PDF");

        ArgumentCaptor<CvVersion> cap = ArgumentCaptor.forClass(CvVersion.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().format()).isEqualTo("PDF");
    }

    @Test
    void upload_sets_version_number_to_1() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CvVersion result = service.uploadCv(userId, "Resume", "content", null);
        assertThat(result.versionNumber()).isEqualTo(1);
    }

    @Test
    void upload_sets_null_id_for_repo_to_assign() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.uploadCv(userId, "Resume", "content", null);

        ArgumentCaptor<CvVersion> cap = ArgumentCaptor.forClass(CvVersion.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().id()).isNull();
    }

    @Test
    void upload_preserves_name_and_content() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CvVersion result = service.uploadCv(userId, "My CV", "# Skills\nJava", "MARKDOWN");
        assertThat(result.name()).isEqualTo("My CV");
        assertThat(result.content()).isEqualTo("# Skills\nJava");
        assertThat(result.userId()).isEqualTo(userId);
    }

    @Test
    void upload_returns_saved_cv() {
        CvVersion saved = cv(cvId, userId, "Resume", "MARKDOWN");
        when(repo.save(any())).thenReturn(saved);

        assertThat(service.uploadCv(userId, "Resume", "content", null)).isSameAs(saved);
    }

    // ── getCvVersions ─────────────────────────────────────────────────────────

    @Test
    void get_cv_versions_delegates_to_repo() {
        List<CvVersion> list = List.of(cv(cvId, userId, "Resume", "MARKDOWN"));
        when(repo.findByUserId(userId)).thenReturn(list);
        assertThat(service.getCvVersions(userId)).isSameAs(list);
    }

    @Test
    void get_cv_versions_returns_empty_list_when_no_cvs() {
        when(repo.findByUserId(userId)).thenReturn(List.of());
        assertThat(service.getCvVersions(userId)).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CvVersion cv(UUID id, UUID userId, String name, String format) {
        return new CvVersion(id, userId, name, "content", format,
                null, false, 1, Instant.now(), Instant.now());
    }
}
