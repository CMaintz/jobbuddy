package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.document.CvVersion;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    // ── helpers ───────────────────────────────────────────────────────────────

    private CvVersion cv(UUID id, UUID userId, String name, String format) {
        return new CvVersion(id, userId, name, "content", format,
                null, false, 1, Instant.now(), Instant.now());
    }
}
