package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.AiAnalysisResult;
import com.autoapplicant.domain.ai.RiskAssessment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-set regression harness for the AI analysis response parser. Fixtures are recorded-style
 * model outputs; the assertions lock in the deterministic mapping (server-side score weighting,
 * dimensions, risk block, enum normalization, fallback) so a prompt/model/parser change that
 * degrades extraction fails fast — without ever calling a model.
 */
class AnalysisResponseParserTest {

    private final AnalysisResponseParser parser = new AnalysisResponseParser(new ObjectMapper());

    private static final String JOB_TARGETED = """
            {
              "score": 50,
              "summary": "Strong backend fit with a location caveat.",
              "strengths": ["Deep Java/Spring experience", "Kubernetes in production"],
              "gaps": ["No Kafka experience stated"],
              "suggestions": ["Add the payments project's throughput numbers"],
              "dimensions": {
                "technicalSkills": 82, "experience": 78, "cultureFit": 70, "careerAlignment": 75,
                "location": "FLAG", "locationNote": "Hybrid in Aarhus; candidate is in Copenhagen"
              },
              "risk": {
                "legitimacy": "SUSPICIOUS",
                "legitimacyNote": "Reposted 4 times in 60 days",
                "signals": [
                  {"label": "Stale posting", "severity": "MEDIUM", "note": "Live 74 days"},
                  {"label": "", "severity": "LOW", "note": "dropped: blank label"}
                ],
                "compensationReliability": "LOW",
                "compensationNote": "Range is OTE, not base"
              }
            }""";

    @Test
    void jobTargeted_scoreIsServerWeighted_notTheModelsNumber() {
        AiAnalysisResult r = parser.parse(JOB_TARGETED);
        assertThat(r.dimensions()).isNotNull();
        assertThat(r.dimensions().isComplete()).isTrue();
        // The model said 50; the server recomputes from weighted dimensions and ignores it.
        assertThat(r.score()).isEqualTo(r.dimensions().weightedScore());
        assertThat(r.strengths()).hasSize(2);
        assertThat(r.dimensions().location()).isEqualTo("FLAG");
    }

    @Test
    void jobTargeted_riskParsedAndSeparateFromScore() {
        RiskAssessment risk = parser.parse(JOB_TARGETED).risk();
        assertThat(risk).isNotNull();
        assertThat(risk.legitimacy()).isEqualTo(RiskAssessment.Legitimacy.SUSPICIOUS);
        assertThat(risk.compensationReliability()).isEqualTo(RiskAssessment.CompensationReliability.LOW);
        // blank-label signal is dropped
        assertThat(risk.signals()).extracting(RiskAssessment.RiskSignal::label)
                .containsExactly("Stale posting");
    }

    @Test
    void generalAnalysis_usesModelScore_noDimensionsOrRisk() {
        String general = """
                {"score": 71, "summary": "Solid generalist CV.",
                 "strengths": ["Clear achievements"], "gaps": ["Thin on metrics"],
                 "suggestions": ["Quantify the migration project"]}""";
        AiAnalysisResult r = parser.parse(general);
        assertThat(r.score()).isEqualTo(71);
        assertThat(r.dimensions()).isNull();
        assertThat(r.risk()).isNull();
    }

    @Test
    void enumNormalization_handlesSpacesHyphensAndCase() {
        String fixture = """
                {"score": 60, "risk": {"legitimacy": "high confidence",
                 "compensationReliability": "unknown-ish", "signals": []}}""";
        RiskAssessment risk = parser.parse(fixture).risk();
        assertThat(risk.legitimacy()).isEqualTo(RiskAssessment.Legitimacy.HIGH_CONFIDENCE);
        // unrecognized value falls back to UNKNOWN rather than throwing
        assertThat(risk.compensationReliability()).isEqualTo(RiskAssessment.CompensationReliability.UNKNOWN);
    }

    @Test
    void codeFencedJson_isStillParsed() {
        String fenced = "```json\n{\"score\": 80, \"summary\": \"ok\"}\n```";
        assertThat(parser.parse(fenced).score()).isEqualTo(80);
    }

    @Test
    void malformedResponse_fallsBackToUnstructured() {
        AiAnalysisResult r = parser.parse("Sorry, I could not analyze that.");
        assertThat(r.score()).isZero();
        assertThat(r.dimensions()).isNull();
        assertThat(r.suggestions()).containsExactly("Sorry, I could not analyze that.");
    }
}
