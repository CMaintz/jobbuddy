package com.autoapplicant.usecase.document;

import static com.autoapplicant.usecase.document.LetterGateTest.answer;
import static com.autoapplicant.usecase.document.LetterGateTest.passing;
import static com.autoapplicant.usecase.document.LetterGateTest.plain;
import static com.autoapplicant.usecase.document.LetterGateTest.point1;
import static com.autoapplicant.usecase.document.LetterGateTest.point14;
import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.LetterGateStop;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.domain.document.structured.LetterReview;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Gate, rewrite from the gate's quotes, gate again: bounded by rounds and by the clock. */
class LetterGateLoopTest {

    private static final String DRAFT = "Udkast.";
    private static final List<String> NEEDS = List.of("680 chauffører uden skærm skal følge reglerne");
    private static final String ECHO_QUOTE = "I søger en, der kan X, Y og Z.";
    private static final int MAX_ROUNDS = 3;
    private static final Instant START = Instant.parse("2026-09-01T12:00:00Z");
    private static final long CALL_TIMEOUT_SECONDS = 120;
    private static final LetterBrief BRIEF = new LetterBrief("COVER_LETTER", "Opslag", "Danish", "DK");

    /** Plays both the gate and the rewriter, each from its own script. */
    static final class TwoRoleModel implements ChatProviderPort {
        final Deque<String> gateAnswers = new ArrayDeque<>();
        final Deque<String> rewrites = new ArrayDeque<>();
        final List<String> operations = new ArrayList<>();
        final List<PromptComposition> rewritePrompts = new ArrayList<>();

        @Override
        public AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation) {
            operations.add(operation);
            String next;
            if (AiOperations.LETTER_REWRITE.equals(operation)) {
                rewritePrompts.add(composition);
                next = rewrites.poll();
            } else {
                next = gateAnswers.poll();
            }
            if (next == null || next.equals("THROW")) {
                throw new IllegalStateException("provider down");
            }
            return AiCompletion.untracked(next, "scripted");
        }

        @Override
        public String chatModelName() {
            return "scripted";
        }

        long count(String operation) {
            return operations.stream().filter(operation::equals).count();
        }
    }

    private static String failing() {
        return answer(point1(true, 9, 14), plain(8, true), plain(13, false, ECHO_QUOTE),
                point14(true, "[1]", 0), "PORT: DUMPET");
    }

    private static String rewrite(String body) {
        return "{\"body\":\"" + body + "\"}";
    }

    private final TwoRoleModel model = new TwoRoleModel();

    private LetterGateLoop loop(Clock clock) {
        ObjectMapper json = new ObjectMapper();
        return new LetterGateLoop(new LetterGate(model, json), new LetterRewriter(model, json),
                MAX_ROUNDS, CALL_TIMEOUT_SECONDS, clock);
    }

    private LetterGateLoop.Outcome run() {
        return runUntil(START.plus(Duration.ofMinutes(20)));
    }

    private LetterGateLoop.Outcome runUntil(Instant deadline) {
        return loop(Clock.fixed(START, ZoneOffset.UTC)).run(DRAFT, NEEDS, BRIEF, deadline);
    }

    @Test
    void aLetterThatPassesIsNotRewritten() {
        // Bug: every letter is rewritten whatever the gate says, doubling the calls.
        model.gateAnswers.add(passing());

        LetterGateLoop.Outcome outcome = run();

        assertThat(outcome.body()).isEqualTo(DRAFT);
        assertThat(outcome.review().gate().verdict()).isEqualTo(LetterGateVerdict.PASSED);
        assertThat(model.count(AiOperations.LETTER_REWRITE)).isZero();
    }

    @Test
    void aFailedLetterIsRewrittenFromTheGatesQuotesAndGatedAgain() {
        // Bug: a failed letter is delivered as it is, or rewritten without being shown what failed.
        model.gateAnswers.add(failing());
        model.gateAnswers.add(passing());
        model.rewrites.add(rewrite("Omskrevet."));

        LetterGateLoop.Outcome outcome = run();

        assertThat(outcome.body()).isEqualTo("Omskrevet.");
        assertThat(outcome.review().gate().verdict()).isEqualTo(LetterGateVerdict.PASSED);
        assertThat(outcome.review().gate().rounds()).isEqualTo(1);
        assertThat(model.rewritePrompts.get(0).resolvedFinalPrompt()).contains(ECHO_QUOTE, NEEDS.get(0));
    }

    @Test
    void theLoopStopsAfterTheLastRoundAndGatesTheLastRewrite() {
        // Bug: the loop never ends; or the last rewrite is delivered ungated, so the stored
        // verdict describes a letter the user never sees.
        for (int i = 0; i <= MAX_ROUNDS; i++) {
            model.gateAnswers.add(failing());
        }
        for (int i = 1; i <= MAX_ROUNDS; i++) {
            model.rewrites.add(rewrite("Runde " + i + "."));
        }

        LetterGateLoop.Outcome outcome = run();

        assertThat(model.count(AiOperations.LETTER_REWRITE)).isEqualTo(MAX_ROUNDS);
        assertThat(model.count(AiOperations.LETTER_GATE)).isEqualTo(MAX_ROUNDS + 1);
        assertThat(outcome.body()).isEqualTo("Runde 3.");
        assertThat(outcome.review().gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(outcome.review().gate().failedPoints()).isNotEmpty();
        assertThat(outcome.review().gate().stop()).isEqualTo(LetterGateStop.OUT_OF_ROUNDS);
        assertThat(outcome.review().gatedBodyFingerprint()).isEqualTo(LetterReview.fingerprint("Runde 3."));
    }

    @Test
    void noVerdictMeansNoRewrite() {
        // Bug: a gate that gave no verdict triggers a rewrite with nothing to act on.
        model.gateAnswers.add("ikke json");
        model.gateAnswers.add("stadig ikke json");

        LetterGateLoop.Outcome outcome = run();

        assertThat(outcome.review().gate().verdict()).isEqualTo(LetterGateVerdict.NOT_COMPLETED);
        assertThat(model.count(AiOperations.LETTER_REWRITE)).isZero();
        assertThat(outcome.body()).isEqualTo(DRAFT);
    }

    @Test
    void noRoundStartsThatCannotFinishBeforeTheDeadline() {
        // Bug: a round is started with too little time left, the request times out, and the
        // letter saved behind the user's back carries no verdict.
        model.gateAnswers.add(failing());
        model.rewrites.add(rewrite("Omskrevet."));

        LetterGateLoop.Outcome outcome = runUntil(START.plus(Duration.ofSeconds(200)));

        assertThat(model.count(AiOperations.LETTER_REWRITE)).isZero();
        assertThat(outcome.body()).isEqualTo(DRAFT);
        assertThat(outcome.review().gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(outcome.review().gate().stop()).isEqualTo(LetterGateStop.OUT_OF_TIME);
    }

    @Test
    void theRoundReserveCoversTheGatesRetry() {
        // Bug: the reserve counts the rewrite and one gate call, so a round whose gate needs its
        // retry overruns the deadline. Two calls fit here; the three a round can take do not.
        model.gateAnswers.add(failing());
        model.rewrites.add(rewrite("Omskrevet."));

        runUntil(START.plus(Duration.ofSeconds(CALL_TIMEOUT_SECONDS * 2 + 1)));

        assertThat(model.count(AiOperations.LETTER_REWRITE)).isZero();
    }

    @Test
    void aRewriteThatFailsKeepsTheLastGatedLetter() {
        // Bug: a rewrite error loses the letter, or delivers text the gate never judged.
        model.gateAnswers.add(failing());
        model.rewrites.add("THROW");

        LetterGateLoop.Outcome outcome = run();

        assertThat(outcome.body()).isEqualTo(DRAFT);
        assertThat(outcome.review().gate().verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(outcome.review().gatedBodyFingerprint()).isEqualTo(LetterReview.fingerprint(DRAFT));
    }

    @Test
    void theRewriterWorksFromTheNeedsAndTheLetterNotTheProfile() {
        // Bug: the rewriter is handed the profile and adds new claims; it may only reshape or cut.
        model.gateAnswers.add(failing());
        model.gateAnswers.add(passing());
        model.rewrites.add(rewrite("Omskrevet."));

        run();

        String prompt = model.rewritePrompts.get(0).resolvedFinalPrompt();
        assertThat(prompt).contains(DRAFT, "Employer Needs", "Letter Rules", "never add");
        assertThat(prompt).doesNotContain("Master Career Profile");
    }
}
