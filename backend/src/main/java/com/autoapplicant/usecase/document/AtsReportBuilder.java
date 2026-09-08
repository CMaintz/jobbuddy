package com.autoapplicant.usecase.document;

import com.autoapplicant.usecase.common.Values;
import com.autoapplicant.domain.document.structured.AtsCheck;
import com.autoapplicant.domain.document.structured.AtsReport;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.domain.document.structured.KeywordCoverage;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import com.autoapplicant.domain.job.JobRequirement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component

public class AtsReportBuilder {

    public AtsReport forTailored(KeywordCoverage coverage, TailoredCvContent tailored, String exportMode,
                                 ContentGuardFindings findings, String documentLanguage) {
        return forTailored(coverage, tailored, exportMode, findings, documentLanguage, List.of());
    }

    public AtsReport forTailored(KeywordCoverage coverage, TailoredCvContent tailored, String exportMode,
                                 ContentGuardFindings findings, String documentLanguage,
                                 List<JobRequirement> requirements) {
        return forCoverage(coverage, tailored.notes(), exportMode, findings, documentLanguage, requirements);
    }

    public AtsReport forCoverage(KeywordCoverage coverage, String exportMode,
                                 ContentGuardFindings findings, String documentLanguage) {
        return forCoverage(coverage, List.of(), exportMode, findings, documentLanguage, List.of());
    }

    public AtsReport forCoverage(KeywordCoverage coverage, String exportMode,
                                 ContentGuardFindings findings, String documentLanguage,
                                 List<JobRequirement> requirements) {
        return forCoverage(coverage, List.of(), exportMode, findings, documentLanguage, requirements);
    }

    private AtsReport forCoverage(KeywordCoverage coverage, List<String> notes, String exportMode,
                                  ContentGuardFindings findings, String documentLanguage,
                                  List<JobRequirement> requirements) {
        KeywordCoverage measured = coverage != null ? coverage : KeywordCoverage.NOT_MEASURED;
        List<AtsCheck> checks = checks(exportMode, notes, findings, documentLanguage);
        keywordCheck(measured, documentLanguage).ifPresent(checks::add);
        requirementCheck(requirements, documentLanguage).ifPresent(checks::add);
        return new AtsReport(
                measured.measured() ? scoreFromCoverage(measured.percent()) : NOT_MEASURED_SCORE,
                measured.percent(),
                measured.matched(),
                measured.missing(),
                checks);
    }

    /**
     * The asks a keyword check cannot settle — years of experience, a degree, a driving licence.
     * Listed, never scored: whether the candidate has five years of backend experience is not
     * something the presence of a word in their CV can establish, and folding it into a
     * percentage would make the percentage a guess. INFO, so it reads as "check these yourself"
     * rather than as a finding against the document.
     */
    private static java.util.Optional<AtsCheck> requirementCheck(List<JobRequirement> requirements,
                                                                 String documentLanguage) {
        if (requirements == null || requirements.isEmpty()) return java.util.Optional.empty();
        List<String> texts = requirements.stream()
                .filter(r -> r.text() != null && !r.text().isBlank())
                .sorted(java.util.Comparator.comparing(r -> r.isRequired() ? 0 : 1))
                .map(r -> r.text().strip())
                .toList();
        if (texts.isEmpty()) return java.util.Optional.empty();
        AtsCheckMessages msg = AtsCheckMessages.forLanguage(documentLanguage);
        return java.util.Optional.of(new AtsCheck("posting_requirements", msg.requirementsLabel(),
                "INFO", msg.requirementsInfo(texts)));
    }

    /**
     * The keyword line. Absent when there was nothing to measure against — an unenriched
     * posting is not the same as a document that covers nothing.
     */
    private static java.util.Optional<AtsCheck> keywordCheck(KeywordCoverage coverage, String documentLanguage) {
        if (!coverage.measured()) return java.util.Optional.empty();
        AtsCheckMessages msg = AtsCheckMessages.forLanguage(documentLanguage);
        int total = coverage.matched().size() + coverage.missing().size();

        if (!coverage.missingRequired().isEmpty()) {
            return java.util.Optional.of(new AtsCheck("keyword_coverage", msg.keywordLabel(), "WARN",
                    msg.keywordWarn(coverage.percent(), coverage.missingRequired())));
        }
        if (coverage.percent() < LOW_COVERAGE) {
            return java.util.Optional.of(new AtsCheck("keyword_coverage", msg.keywordLabel(), "WARN",
                    msg.keywordThin(coverage.percent())));
        }
        return java.util.Optional.of(new AtsCheck("keyword_coverage", msg.keywordLabel(), "PASS",
                msg.keywordPass(coverage.percent(), coverage.matched().size(), total)));
    }

    /** Coverage below this reads as thin rather than adequate. Warn-only; nothing blocks on it. */
    private static final int LOW_COVERAGE = 50;

    /** Score used when there was no keyword list to measure against. */
    private static final int NOT_MEASURED_SCORE = 50;

    public AtsReport basic(String content, String exportMode, ContentGuardFindings findings,
                           String documentLanguage) {
        return basic(content, exportMode, findings, documentLanguage, List.of());
    }

    public AtsReport basic(String content, String exportMode, ContentGuardFindings findings,
                           String documentLanguage, List<JobRequirement> requirements) {
        // No keyword coverage to score against (e.g. a plain document): report a neutral
        // "not measured" score rather than a reassuringly high one.
        int wordCount = content != null && !content.isBlank() ? content.trim().split("\\s+").length : 0;
        int score = wordCount > 0 ? 60 : 50;
        List<AtsCheck> checks = checks(exportMode, List.of(), findings, documentLanguage);
        requirementCheck(requirements, documentLanguage).ifPresent(checks::add);
        return new AtsReport(score, 0, List.of(), List.of(), checks);
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
        for (String note : Values.listOrEmpty(notes)) {
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

}
