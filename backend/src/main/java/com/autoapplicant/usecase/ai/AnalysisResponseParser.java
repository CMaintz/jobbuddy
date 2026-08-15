package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.AiAnalysisResult;
import com.autoapplicant.domain.ai.AnalysisDimensions;
import com.autoapplicant.domain.ai.RiskAssessment;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, deterministic parser for the AI CV-analysis JSON response. Extracted from
 * {@code AiService} so the mapping (dimensions, server-side score weighting, risk block)
 * can be regression-tested against a golden set of recorded responses without invoking a model.
 */
@Service
public class AnalysisResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AnalysisResponseParser.class);

    private final ObjectMapper objectMapper;

    public AnalysisResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Parses the structured analysis; falls back to raw text when the JSON is broken. */
    public AiAnalysisResult parse(String response) {
        try {
            JsonNode node = objectMapper.readTree(AiResponseParser.extractJsonObject(response));
            List<String> suggestions = new ArrayList<>();
            node.path("suggestions").forEach(s -> suggestions.add(s.asText()));
            List<String> strengths = new ArrayList<>();
            node.path("strengths").forEach(s -> strengths.add(s.asText()));
            List<String> gaps = new ArrayList<>();
            node.path("gaps").forEach(s -> gaps.add(s.asText()));

            // Dimensional scores (job-targeted analyses). When complete, the overall
            // score is computed server-side from the fixed weights, not trusted from the model.
            AnalysisDimensions dimensions = parseDimensions(node.path("dimensions"));
            int score = dimensions != null && dimensions.isComplete()
                    ? dimensions.weightedScore()
                    : Math.max(0, Math.min(100, node.path("score").asInt(0)));

            return new AiAnalysisResult(suggestions, score, response,
                    node.path("summary").asText(null),
                    strengths, gaps, dimensions, parseRisk(node.path("risk")));
        } catch (Exception e) {
            log.warn("Analysis response was not valid JSON — returning raw text: {}", e.getMessage());
            return AiAnalysisResult.unstructured(response);
        }
    }

    /** Parses the posting/employer risk block (job-targeted analyses only); null when absent. */
    private static RiskAssessment parseRisk(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        List<RiskAssessment.RiskSignal> signals = new ArrayList<>();
        for (JsonNode s : node.path("signals")) {
            String label = s.path("label").asText(null);
            if (label == null || label.isBlank()) continue;
            signals.add(new RiskAssessment.RiskSignal(
                    label, s.path("severity").asText("LOW"), s.path("note").asText(null)));
        }
        return new RiskAssessment(
                enumOrDefault(node.path("legitimacy").asText(null),
                        RiskAssessment.Legitimacy.class, RiskAssessment.Legitimacy.NOT_ASSESSED),
                node.path("legitimacyNote").asText(null),
                signals,
                enumOrDefault(node.path("compensationReliability").asText(null),
                        RiskAssessment.CompensationReliability.class, RiskAssessment.CompensationReliability.UNKNOWN),
                node.path("compensationNote").asText(null));
    }

    private static <E extends Enum<E>> E enumOrDefault(String value, Class<E> type, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static AnalysisDimensions parseDimensions(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        return new AnalysisDimensions(
                intOrNull(node, "technicalSkills"),
                intOrNull(node, "experience"),
                intOrNull(node, "cultureFit"),
                intOrNull(node, "careerAlignment"),
                node.path("location").asText(null),
                node.path("locationNote").asText(null));
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }
}
