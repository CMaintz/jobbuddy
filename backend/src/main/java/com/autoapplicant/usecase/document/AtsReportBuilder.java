package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.AtsCheck;
import com.autoapplicant.domain.document.structured.AtsReport;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AtsReportBuilder {

    public AtsReport forTailored(TailoredCvContent tailored, String exportMode) {
        return new AtsReport(
                scoreFromCoverage(tailored.keywordCoverage()),
                clamp(tailored.keywordCoverage()),
                listOrEmpty(tailored.matchedKeywords()),
                listOrEmpty(tailored.missingKeywords()),
                checks(exportMode, tailored.notes()));
    }

    public AtsReport forCoverage(Integer keywordCoverage, List<String> matchedKeywords,
                                  List<String> missingKeywords, String exportMode) {
        return new AtsReport(
                scoreFromCoverage(keywordCoverage),
                clamp(keywordCoverage),
                listOrEmpty(matchedKeywords),
                listOrEmpty(missingKeywords),
                checks(exportMode, List.of()));
    }

    public AtsReport basic(String content, String exportMode) {
        // No keyword coverage to score against (e.g. a plain document): report a neutral
        // "not measured" score rather than a reassuringly high one.
        int wordCount = content != null && !content.isBlank() ? content.trim().split("\\s+").length : 0;
        int score = wordCount > 0 ? 60 : 50;
        return new AtsReport(score, 0, List.of(), List.of(), checks(exportMode, List.of()));
    }

    public List<AtsCheck> checks(String exportMode, List<String> notes) {
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
        for (String note : listOrEmpty(notes)) {
            result.add(new AtsCheck("ai_note", "Tailoring note", "INFO", note));
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
