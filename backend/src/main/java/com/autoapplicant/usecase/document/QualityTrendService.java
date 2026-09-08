package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.RecordedQualityScore;
import com.autoapplicant.port.in.document.GetQualityTrendUseCase;
import com.autoapplicant.port.out.document.QualityScoreRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Reads back the quality scores generation recorded, so the trend is visible in the app rather
 * than only in the server log.
 */
@Service
public class QualityTrendService implements GetQualityTrendUseCase {

    /** Enough history to see a trend without turning the endpoint into a bulk export. */
    private static final int MAX_LIMIT = 100;

    private final QualityScoreRepositoryPort repo;

    public QualityTrendService(QualityScoreRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<RecordedQualityScore> recentScores(UUID userId, int limit) {
        return repo.findRecent(userId, Math.min(Math.max(1, limit), MAX_LIMIT));
    }
}
