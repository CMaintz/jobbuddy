package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentJobServiceTest {

    @Test
    void documents_for_a_job_are_looked_up_for_the_caller_only() {
        GeneratedDocumentRepositoryPort repo = mock(GeneratedDocumentRepositoryPort.class);
        UUID jobId = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(repo.findByJobIdAndUserId(jobId, bob)).thenReturn(List.of());

        List<GeneratedDocument> docs = new DocumentJobService(repo).getDocumentsForJob(jobId, bob);

        assertThat(docs).isEmpty();
        verify(repo).findByJobIdAndUserId(jobId, bob);
        verifyNoMoreInteractions(repo);
    }
}
