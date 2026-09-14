package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Instant;
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
class IngestionPipelineTest {

    @Mock JobRepositoryPort     jobRepo;
    @Mock TextCleaningService   textCleaner;
    @Mock CompanyRepositoryPort companyRepo;

    IngestionPipeline pipeline;

    // Enough distinct words that SimHash.fingerprint(...) is non-zero (short text fingerprints to 0).
    private static final String RICH_TEXT =
            "Senior backend engineer building resilient distributed systems with Java Spring "
            + "Postgres Kafka and Kubernetes across several product teams in Copenhagen";

    @BeforeEach
    void setUp() {
        pipeline = new IngestionPipeline(jobRepo, textCleaner, companyRepo);
    }

    @Test
    void refreshes_existing_job_by_source_id_without_saving() {
        RawJobData raw = raw("src-1");
        Job seen = Job.builder().id(UUID.randomUUID()).source(JobSource.TEAMTAILOR).build();
        when(jobRepo.findBySourceAndSourceJobId(JobSource.TEAMTAILOR, "src-1"))
                .thenReturn(Optional.of(seen));

        IngestionPipeline.IngestResult result = pipeline.ingest(raw);

        assertThat(result.outcome()).isEqualTo(IngestionPipeline.IngestOutcome.REFRESHED);
        assertThat(result.crossListed()).isFalse();
        verify(jobRepo).refreshLastSeen(eq(seen.id()), any(), any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void saves_new_job_when_not_seen_before() {
        RawJobData raw = raw("src-2");
        when(jobRepo.findBySourceAndSourceJobId(any(), any())).thenReturn(Optional.empty());
        stubCleaning();
        UUID savedId = UUID.randomUUID();
        when(jobRepo.save(any())).thenReturn(job(savedId));
        when(jobRepo.findActiveDuplicateByFingerprint(anyLong(), any())).thenReturn(Optional.empty());

        IngestionPipeline.IngestResult result = pipeline.ingest(raw);

        assertThat(result.outcome()).isEqualTo(IngestionPipeline.IngestOutcome.NEW);
        assertThat(result.crossListed()).isFalse();
        verify(jobRepo).save(any());
        verify(jobRepo).assignContentFingerprint(eq(savedId), anyLong());
        verify(jobRepo, never()).assignDuplicateGroup(any(), any());
    }

    @Test
    void clusters_cross_listed_duplicate_under_one_group() {
        RawJobData raw = raw("src-3");
        when(jobRepo.findBySourceAndSourceJobId(any(), any())).thenReturn(Optional.empty());
        stubCleaning();
        UUID savedId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        when(jobRepo.save(any())).thenReturn(job(savedId));
        Job other = Job.builder().id(otherId).source(JobSource.TEAMTAILOR).build(); // no group yet
        when(jobRepo.findActiveDuplicateByFingerprint(anyLong(), eq(savedId)))
                .thenReturn(Optional.of(other));

        IngestionPipeline.IngestResult result = pipeline.ingest(raw);

        assertThat(result.outcome()).isEqualTo(IngestionPipeline.IngestOutcome.NEW);
        assertThat(result.crossListed()).isTrue();
        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> group = ArgumentCaptor.forClass(UUID.class);
        verify(jobRepo, times(2)).assignDuplicateGroup(id.capture(), group.capture());
        assertThat(id.getAllValues()).containsExactlyInAnyOrder(otherId, savedId);
        assertThat(group.getAllValues().get(0)).isEqualTo(group.getAllValues().get(1)); // one shared group
    }

    @Test
    void resolves_company_and_backfills_missing_website() {
        RawJobData raw = new RawJobData(JobSource.TEAMTAILOR, "src-4", "https://x/4", "<p>b</p>",
                null, Instant.now(), List.of(), null, null, "Acme A/S", "https://acme.dk", "CPH", null);
        when(jobRepo.findBySourceAndSourceJobId(any(), any())).thenReturn(Optional.empty());
        stubCleaning();
        UUID companyId = UUID.randomUUID();
        when(companyRepo.findOrCreate("Acme A/S")).thenReturn(company(companyId, null)); // no website yet
        when(jobRepo.save(any())).thenReturn(job(UUID.randomUUID()));
        when(jobRepo.findActiveDuplicateByFingerprint(anyLong(), any())).thenReturn(Optional.empty());

        pipeline.ingest(raw);

        verify(companyRepo).backfillWebsite(companyId, "https://acme.dk");
        ArgumentCaptor<Job> draft = ArgumentCaptor.forClass(Job.class);
        verify(jobRepo).save(draft.capture());
        assertThat(draft.getValue().companyId()).isEqualTo(companyId);
    }

    @Test
    void returns_failed_when_persistence_throws() {
        RawJobData raw = raw("src-5");
        when(jobRepo.findBySourceAndSourceJobId(any(), any())).thenReturn(Optional.empty());
        stubCleaning();
        when(jobRepo.save(any())).thenThrow(new RuntimeException("db down"));

        IngestionPipeline.IngestResult result = pipeline.ingest(raw);

        assertThat(result.outcome()).isEqualTo(IngestionPipeline.IngestOutcome.FAILED);
    }

    private void stubCleaning() {
        when(textCleaner.clean(any())).thenReturn(RICH_TEXT);
        when(textCleaner.extractTitle(any())).thenReturn("Backend Engineer");
    }

    private static RawJobData raw(String sourceJobId) {
        return new RawJobData(JobSource.TEAMTAILOR, sourceJobId, "https://x/" + sourceJobId,
                "<p>body</p>", null, Instant.now(), List.of(), null);
    }

    private static Job job(UUID id) {
        return Job.builder().id(id).source(JobSource.TEAMTAILOR).build();
    }

    private static Company company(UUID id, String website) {
        return new Company(id, "Acme A/S", "acme", website, null, null, null,
                null, null, "DK", false, false, null, null);
    }
}
