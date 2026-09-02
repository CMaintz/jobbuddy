package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.skill.EvidenceDraft;
import com.autoapplicant.domain.skill.EvidenceGap;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EvidenceElicitationServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final String ANSWER =
            "i moved 30 services to k8s at netcompany, deploys went from 40 min to 9";

    private final ChatProviderPort ai = Mockito.mock(ChatProviderPort.class);
    private final CareerProfileContextService careerProfileContext =
            Mockito.mock(CareerProfileContextService.class);

    private final EvidenceElicitationService service = new EvidenceElicitationService(
            ai, careerProfileContext, new DocumentFactGuard(), new ObjectMapper());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(careerProfileContext.buildJson(any())).thenReturn("{}");
    }

    private void aiReturns(String json) {
        when(ai.generateJson(any(PromptComposition.class), any())).thenReturn(json);
    }

    private void aiFails() {
        when(ai.generateJson(any(PromptComposition.class), any()))
                .thenThrow(new IllegalStateException("provider down"));
    }

    // ── Question tailoring ────────────────────────────────────────────────

    @Test
    void aTailoredQuestionReplacesTheTemplate() {
        aiReturns("""
                {"questions":[{"skill":"Kubernetes","question":"Which cluster did you run, and what broke?"}]}""");

        List<EvidenceGap> tailored = service.tailorQuestions(USER,
                List.of(new EvidenceGap("Kubernetes", 3, "template question")));

        assertThat(tailored).singleElement().extracting(EvidenceGap::question)
                .isEqualTo("Which cluster did you run, and what broke?");
    }

    @Test
    void aSkillTheModelSkippedKeepsItsTemplateQuestion() {
        aiReturns("{\"questions\":[]}");
        assertThat(service.tailorQuestions(USER, List.of(new EvidenceGap("Kubernetes", 3, "template")))
                .getFirst().question()).isEqualTo("template");
    }

    @Test
    void aProviderFailureCostsPolishNotTheFeature() {
        aiFails();
        assertThat(service.tailorQuestions(USER, List.of(new EvidenceGap("Kubernetes", 3, "template")))
                .getFirst().question()).isEqualTo("template");
    }

    @Test
    void disablingTheAiLeavesTheTemplatesUntouched() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.tailorQuestions(USER, List.of(new EvidenceGap("Kubernetes", 3, "template")))
                .getFirst().question()).isEqualTo("template");
        Mockito.verifyNoInteractions(ai);
    }

    // ── Answer drafting ───────────────────────────────────────────────────

    @Test
    void aFreeTextAnswerBecomesTheThreeFields() {
        aiReturns("""
                {"situation":"Platform team at Netcompany","action":"Moved 30 services to Kubernetes",
                 "result":"Deploys went from 40 minutes to 9"}""");

        EvidenceDraft draft = service.draftFromAnswer(USER, "Kubernetes", ANSWER);
        assertThat(draft.situation()).isEqualTo("Platform team at Netcompany");
        assertThat(draft.action()).isEqualTo("Moved 30 services to Kubernetes");
        assertThat(draft.isFaithful()).isTrue();
    }

    @Test
    void aFigureTheUserNeverWroteIsReportedRatherThanKept() {
        // The model was told to add nothing; this is where that is checked instead of trusted.
        aiReturns("""
                {"situation":"Netcompany","action":"Moved 30 services to Kubernetes",
                 "result":"Deploys went from 40 minutes to 9, saving 200 hours a month"}""");

        EvidenceDraft draft = service.draftFromAnswer(USER, "Kubernetes", ANSWER);
        assertThat(draft.isFaithful()).isFalse();
        assertThat(draft.unsupportedFigures()).anyMatch(f -> f.contains("200"));
    }

    @Test
    void anEmptyDraftFallsBackToWhatTheUserActuallyWrote() {
        aiReturns("{\"situation\":null,\"action\":null,\"result\":null}");
        EvidenceDraft draft = service.draftFromAnswer(USER, "Kubernetes", ANSWER);
        assertThat(draft.action()).isEqualTo(ANSWER);
    }

    @Test
    void aProviderFailureNeverLosesTheAnswer() {
        aiFails();
        assertThat(service.draftFromAnswer(USER, "Kubernetes", ANSWER).action()).isEqualTo(ANSWER);
    }

    @Test
    void malformedJsonNeverLosesTheAnswer() {
        aiReturns("not json at all");
        assertThat(service.draftFromAnswer(USER, "Kubernetes", ANSWER).action()).isEqualTo(ANSWER);
    }

    @Test
    void thereMustBeSomethingToDraftFrom() {
        assertThatThrownBy(() -> service.draftFromAnswer(USER, "Kubernetes", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.draftFromAnswer(USER, " ", ANSWER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
