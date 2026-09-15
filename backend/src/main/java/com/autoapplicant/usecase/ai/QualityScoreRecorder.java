package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.QualityScore;
import com.autoapplicant.domain.document.RecordedQualityScore;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.document.QualityScoreRepositoryPort;
import com.autoapplicant.usecase.document.PromptCompositionBuilder;
import com.autoapplicant.usecase.eval.DocumentQualityEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Scores the delivered document and records it against the saved document, so prompt changes are
 * observable on real output rather than only on test fixtures. Deterministic and cheap — no model
 * call — and never allowed to break generation: a scoring failure is a lost metric, not a lost
 * document.
 *
 * <p>Only prose letters are scored; the short recruiter and follow-up messages carry their own word
 * caps and would be measured against the wrong target.
 */
@Service
public class QualityScoreRecorder {

    private static final Logger log = LoggerFactory.getLogger(QualityScoreRecorder.class);

    private final DocumentQualityEvaluator qualityEvaluator;
    private final QualityScoreRepositoryPort qualityScoreRepo;

    public QualityScoreRecorder(DocumentQualityEvaluator qualityEvaluator,
                                QualityScoreRepositoryPort qualityScoreRepo) {
        this.qualityEvaluator = qualityEvaluator;
        this.qualityScoreRepo = qualityScoreRepo;
    }

    public void record(GenerateDocumentCommand cmd, GenerationInputs in, StructuredDocument saved, String body) {
        if (!PromptCompositionBuilder.isProseLetter(cmd.documentType())) {
            return;
        }
        try {
            QualityScore score = qualityEvaluator.evaluate(body, in.contactFreeJson(), keywordsOf(in.job()),
                    PromptCompositionBuilder.letterWordTarget(cmd.lengthPreference()));
            log.info("Quality score for {}: {} [{}]", cmd.documentType(), score.total(),
                    score.dimensions().stream()
                            .map(d -> d.code() + "=" + d.score())
                            .collect(Collectors.joining(" ")));
            qualityScoreRepo.save(new RecordedQualityScore(null, cmd.userId(),
                    saved != null ? saved.generatedDocumentId() : null,
                    cmd.documentType(), score, null));
        } catch (Exception e) {
            log.debug("Quality scoring failed (non-fatal): {}", e.getMessage());
        }
    }

    private static List<String> keywordsOf(Job job) {
        List<String> keywords = new ArrayList<>();
        if (job != null) {
            if (job.technologies() != null) {
                keywords.addAll(job.technologies());
            }
            if (job.skills() != null) {
                keywords.addAll(job.skills());
            }
        }
        return keywords;
    }
}
