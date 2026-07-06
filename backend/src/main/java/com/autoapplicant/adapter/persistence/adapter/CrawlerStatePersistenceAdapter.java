package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.CrawlerStateEntity;
import com.autoapplicant.adapter.persistence.repository.CrawlerStateJpaRepository;
import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CrawlerStatePersistenceAdapter implements CrawlerStateRepositoryPort {

    private final CrawlerStateJpaRepository repo;

    public CrawlerStatePersistenceAdapter(CrawlerStateJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public CrawlerState save(CrawlerState state) {
        CrawlerStateEntity e = repo.findBySource(state.source()).orElse(new CrawlerStateEntity());
        e.setSource(state.source());
        e.setPageOffset(state.pageOffset());
        e.setLastCrawlStartedAt(state.lastCrawlStartedAt());
        e.setLastCrawlFinishedAt(state.lastCrawlFinishedAt());
        e.setJobsFound(state.jobsFound());
        e.setJobsIngested(state.jobsIngested());
        e.setLastError(state.lastError());
        e.setRunning(state.isRunning());
        return toDomain(repo.save(e));
    }

    @Override
    public Optional<CrawlerState> findBySource(String source) {
        return repo.findBySource(source).map(this::toDomain);
    }

    @Override
    public List<CrawlerState> findAll() {
        return repo.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    private CrawlerState toDomain(CrawlerStateEntity e) {
        return new CrawlerState(e.getSource(), e.getPageOffset(),
                e.getLastCrawlStartedAt(), e.getLastCrawlFinishedAt(),
                e.getJobsFound(), e.getJobsIngested(), e.getLastError(),
                e.isRunning(), e.getUpdatedAt());
    }
}
