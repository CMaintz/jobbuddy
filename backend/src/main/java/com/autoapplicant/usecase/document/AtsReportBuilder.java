package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.AtsCheck;
import com.autoapplicant.domain.document.structured.AtsReport;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AtsReportBuilder {

    public AtsReport forTailored(TailoredCvContent tailored, String exportMode,
                                 ContentGuardFindings findings, String documentLanguage) {
        return new AtsReport(
                scoreFromCoverage(tailored.keywordCoverage()),
                clamp(tailored.keywordCoverage()),
                listOrEmpty(tailored.matchedKeywords()),
                listOrEmpty(tailored.missingKeywords()),
                checks(exportMode, tailored.notes(), findings, documentLanguage));
    }

    public AtsReport forCoverage(Integer keywordCoverage, List<String> matchedKeywords,
                                  List<String> missingKeywords, String exportMode,
                                  ContentGuardFindings findings, String documentLanguage) {
        return new AtsReport(
                scoreFromCoverage(keywordCoverage),
                clamp(keywordCoverage),
                listOrEmpty(matchedKeywords),
                listOrEmpty(missingKeywords),
                checks(exportMode, List.of(), findings, documentLanguage));
    }

    public AtsReport basic(String content, String exportMode, ContentGuardFindings findings,
                           String documentLanguage) {
        // No keyword coverage to score against (e.g. a plain document): report a neutral
        // "not measured" score rather than a reassuringly high one.
        int wordCount = content != null && !content.isBlank() ? content.trim().split("\\s+").length : 0;
        int score = wordCount > 0 ? 60 : 50;
        return new AtsReport(score, 0, List.of(), List.of(),
                checks(exportMode, List.of(), findings, documentLanguage));
    }

    public List<AtsCheck> checks(String exportMode, List<String> notes, ContentGuardFindings findings,
                                 String documentLanguage) {
        AtsCheckMessages msg = AtsCheckMessages.forLanguage(documentLanguage);
        List<AtsCheck> result = new ArrayList<>();
        result.add(new AtsCheck("real_text", msg.realTextLabel(), "PASS", msg.realTextDetail()));
        result.add(new AtsCheck("standard_sections", msg.headingsLabel(), "PASS", msg.headingsDetail()));
        result.add(new AtsCheck("contact_privacy", msg.privacyLabel(), "PASS", msg.privacyDetail()));
        if ("DESIGNED".equalsIgnoreCase(exportMode)) {
            result.add(new AtsCheck("layout_complexity", msg.designedLabel(), "WARN", msg.designedDetail()));
        }
        result.addAll(guardChecks(findings != null ? findings : ContentGuardFindings.NONE, msg));
        for (String note : listOrEmpty(notes)) {
            // The note itself comes from the model, already in the document's language.
            result.add(new AtsCheck("ai_note", msg.noteLabel(), "INFO", note));
        }
        return result;
    }

    /**
     * Turns deterministic guard findings into user-facing checks. The two guards that run on every
     * document report either way — a green line is the evidence that the check ran — while
     * retracted claims only appear when violated, since most users have none and a permanent PASS
     * for a feature they never used is noise.
     */
    private static List<AtsCheck> guardChecks(ContentGuardFindings findings, AtsCheckMessages msg) {
        List<AtsCheck> result = new ArrayList<>();
        result.add(findings.unsupportedMetrics().isEmpty()
                ? new AtsCheck("fact_guard", msg.metricsLabel(), "PASS", msg.metricsPass())
                : new AtsCheck("fact_guard", msg.metricsLabel(), "FAIL",
                        msg.metricsFail(findings.unsupportedMetrics())));
        if (!findings.unverifiedMetrics().isEmpty()) {
            result.add(new AtsCheck("unverified_metrics", msg.unverifiedLabel(), "WARN",
                    msg.unverifiedWarn(findings.unverifiedMetrics())));
        }
        result.add(findings.fillerPhrases().isEmpty()
                ? new AtsCheck("filler_phrases", msg.fillerLabel(), "PASS", msg.fillerPass())
                : new AtsCheck("filler_phrases", msg.fillerLabel(), "WARN",
                        msg.fillerWarn(findings.fillerPhrases())));
        if (!findings.retractedClaims().isEmpty()) {
            result.add(new AtsCheck("retracted_claims", msg.retractedLabel(), "FAIL",
                    msg.retractedFail(findings.retractedClaims())));
        }
        return result;
    }

    /**
     * Maps keyword coverage (0–100) to a diagnostic ATS score. The score tracks coverage closely
     * so a weak match reads as weak — a small base (3) keeps a near-zero match from looking like a
     * hard zero, and the ceiling (98) leaves headroom below "perfect". Previously this floored at
     * 65, which made every document look reassuringly strong regardless of the actual match.
     */
    static int scoreFromCoverage(Integer coverage) {
        int value = clamp(coverage);
        return Math.min(98, 3 + Math.round(value * 0.95f));
    }

    static int clamp(Integer value) {
        if (value == null) return 0;
        return Math.max(0, Math.min(100, value));
    }

    private static List<String> listOrEmpty(List<String> values) {
        return values != null ? values : List.of();
    }
}
