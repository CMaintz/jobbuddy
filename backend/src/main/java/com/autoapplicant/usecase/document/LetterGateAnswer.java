package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.LetterGatePoint;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the letter gate's answer into a verdict. The model judges, but the verdict is ours: points
 * 1 and 14 are recomputed from the counts the gate reports, a quoted sentence under 13 or 14 is a
 * fail, a missing point or count is a fail ("doubt counts as fail"), and a pass needs the PORT line
 * to agree.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LetterGateAnswer(List<AnswerPoint> points, String verdict) {

    static final List<Integer> POINTS = List.of(1, 8, 13, 14);

    private static final String PASSED_WORD = "BESTÅET";
    private static final Pattern PORT_LINE = Pattern.compile(
            "PORT:\\s*(" + PASSED_WORD + "|DUMPET)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** One point as the model writes it; the counts are present only for points 1 and 14. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnswerPoint(Integer point, Boolean passed, List<String> quotes, String reason,
                       Integer employerSentences, Integer totalSentences,
                       List<Integer> pastTenseApplicantSentencesPerParagraph,
                       Integer paragraphsOpeningWithApplicantInPast) {}

    /** The verdict in {@code raw}, or empty when it is not a readable gate answer. */
    static Optional<LetterGateResult> read(ObjectMapper json, String raw) {
        try {
            LetterGateAnswer answer = json.readValue(
                    AiResponseParser.extractJsonObject(AiResponseParser.sanitize(raw).trim()), LetterGateAnswer.class);
            return answer.toResult();
        } catch (JsonProcessingException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Our verdict, or empty when there is none to give. A failed point is a fail whatever the PORT
     * line says or how it is worded: discarding the answer over a stray "." would ship the letter
     * unrewritten. Only a pass needs the PORT line, and only one that says BESTÅET and not DUMPET;
     * a DUMPET over four passed points names nothing to rewrite from, so it is no verdict.
     */
    private Optional<LetterGateResult> toResult() {
        if (points == null || points.isEmpty()) {
            return Optional.empty();
        }
        List<LetterGatePoint> judged = judged();
        if (!judged.stream().allMatch(LetterGatePoint::passed)) {
            return Optional.of(new LetterGateResult(LetterGateVerdict.FAILED, judged, 0, null));
        }
        return portLineSaysPassed()
                ? Optional.of(new LetterGateResult(LetterGateVerdict.PASSED, judged, 0, null))
                : Optional.empty();
    }

    /** True when the PORT line says BESTÅET and nowhere says DUMPET, whatever surrounds it. */
    private boolean portLineSaysPassed() {
        if (verdict == null) {
            return false;
        }
        boolean passed = false;
        Matcher m = PORT_LINE.matcher(verdict);
        while (m.find()) {
            if (!m.group(1).equalsIgnoreCase(PASSED_WORD)) {
                return false;
            }
            passed = true;
        }
        return passed;
    }

    /** Each checklist point as we judge it; a point the answer left out is a fail. */
    private List<LetterGatePoint> judged() {
        Map<Integer, AnswerPoint> byNumber = points.stream()
                .filter(p -> p != null && p.point() != null)
                .collect(Collectors.toMap(AnswerPoint::point, Function.identity(), (first, second) -> first));
        List<LetterGatePoint> judged = new ArrayList<>();
        for (int number : POINTS) {
            AnswerPoint p = byNumber.get(number);
            judged.add(p == null
                    ? new LetterGatePoint(number, false, List.of(), "The gate did not answer this point.")
                    : new LetterGatePoint(number, passes(p), p.quotes(), p.reason()));
        }
        return judged;
    }

    private static boolean passes(AnswerPoint p) {
        boolean claimed = Boolean.TRUE.equals(p.passed());
        return switch (p.point()) {
            case 1 -> claimed && mostlyAboutTheEmployer(p);
            case 13 -> claimed && nothingQuoted(p);
            case 14 -> claimed && noCvRecap(p) && nothingQuoted(p);
            default -> claimed;
        };
    }

    /** Under 13 and 14 every quoted sentence fails by itself, whatever the gate concluded. */
    private static boolean nothingQuoted(AnswerPoint p) {
        return p.quotes() == null || p.quotes().stream().allMatch(q -> q == null || q.isBlank());
    }

    private static boolean mostlyAboutTheEmployer(AnswerPoint p) {
        return p.employerSentences() != null && p.totalSentences() != null && p.totalSentences() > 0
                && p.employerSentences() * 2 >= p.totalSentences();
    }

    private static boolean noCvRecap(AnswerPoint p) {
        List<Integer> perParagraph = p.pastTenseApplicantSentencesPerParagraph();
        return perParagraph != null && p.paragraphsOpeningWithApplicantInPast() != null
                && perParagraph.stream().allMatch(count -> count != null && count <= 1)
                && p.paragraphsOpeningWithApplicantInPast() == 0;
    }
}
