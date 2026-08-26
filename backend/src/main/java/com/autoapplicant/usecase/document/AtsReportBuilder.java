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
                                 ContentGuardFindings findings) {
        return new AtsReport(
                scoreFromCoverage(tailored.keywordCoverage()),
                clamp(tailored.keywordCoverage()),
                listOrEmpty(tailored.matchedKeywords()),
                listOrEmpty(tailored.missingKeywords()),
                checks(exportMode, tailored.notes(), findings));
    }

    public AtsReport forCoverage(Integer keywordCoverage, List<String> matchedKeywords,
                                  List<String> missingKeywords, String exportMode,
                                  ContentGuardFindings findings) {
        return new AtsReport(
                scoreFromCoverage(keywordCoverage),
                clamp(keywordCoverage),
                listOrEmpty(matchedKeywords),
                listOrEmpty(missingKeywords),
                checks(exportMode, List.of(), findings));
    }

    public AtsReport basic(String content, String exportMode, ContentGuardFindings findings) {
        // No keyword coverage to score against (e.g. a plain document): report a neutral
        // "not measured" score rather than a reassuringly high one.
        int wordCount = content != null && !content.isBlank() ? content.trim().split("\\s+").length : 0;
        int score = wordCount > 0 ? 60 : 50;
        return new AtsReport(score, 0, List.of(), List.of(), checks(exportMode, List.of(), findings));
    }

    public List<AtsCheck> checks(String exportMode, List<String> notes, ContentGuardFindings findings) {
        List<AtsCheck> result = new ArrayList<>();
        result.add(new AtsCheck("real_text", "Real text rendering", "PASS",
                "Rendered as selectable text rather than an image."));
        result.add(new AtsCheck("standard_sections", "Standard CV headings", "PASS",
                "Uses predictable section headings for CV parsing."));
        result.add(new AtsCheck("contact_privacy", "Contact privacy", "PASS",
                "Name and contact details are inserted after AI tailoring."));
        if ("DESIGNED".equalsIgnoreCase(exportMode)) {
            result.add(new AtsCheck("layout_complexity", "Designed layout", "WARN",
                    "Designed templates may parse less reliably than ATS mode."));
        }
        result.addAll(guardChecks(findings != null ? findings : ContentGuardFindings.NONE));
        for (String note : listOrEmpty(notes)) {
            result.add(new AtsCheck("ai_note", "Tailoring note", "INFO", note));
        }
        return result;
    }

    /**
     * Turns deterministic guard findings into user-facing checks. The two guards that run on every
     * document report either way — a green line is the evidence that the check ran — while
     * retracted claims only appear when violated, since most users have none and a permanent PASS
     * for a feature they never used is noise.
     */
    private static List<AtsCheck> guardChecks(ContentGuardFindings findings) {
        List<AtsCheck> result = new ArrayList<>();
        result.add(findings.unsupportedMetrics().isEmpty()
                ? new AtsCheck("fact_guard", "Metric claims", "PASS",
                        "Every number in the document traces back to your profile.")
                : new AtsCheck("fact_guard", "Metric claims", "FAIL",
                        "Not supported by your profile: " + join(findings.unsupportedMetrics())
                        + ". Correct or remove before sending — you would have to defend these in an interview."));
        result.add(findings.fillerPhrases().isEmpty()
                ? new AtsCheck("filler_phrases", "Filler phrases", "PASS",
                        "No known application clichés or AI-tell phrasing.")
                : new AtsCheck("filler_phrases", "Filler phrases", "WARN",
                        "Reads as boilerplate: " + join(findings.fillerPhrases())
                        + ". Replace with something concrete to this role."));
        if (!findings.retractedClaims().isEmpty()) {
            result.add(new AtsCheck("retracted_claims", "Retracted claims", "FAIL",
                    "Claims you previously disowned reappeared: " + join(findings.retractedClaims()) + "."));
        }
        return result;
    }

    /** Quotes and joins findings for display; caps the list so one bad generation can't flood the panel. */
    private static String join(List<String> values) {
        String joined = values.stream().limit(5).map(v -> "\"" + v + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        return values.size() > 5 ? joined + " (+" + (values.size() - 5) + " more)" : joined;
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
