package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateStop;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.domain.document.structured.LetterReview;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gates a letter and, while it fails, rewrites it from the gate's quotes and gates it again.
 *
 * <p>Bounded twice: by {@code app.ai.letter-gate.max-rounds} rewrites, and by the request deadline.
 * A round is one rewrite plus a verdict of up to {@link LetterGate#MAX_CALLS} calls, and it starts
 * only if all of them fit before the deadline at the per-call timeout; a request that times out
 * leaves a letter saved behind the user's back with no verdict shown. Every letter handed back has
 * been gated, so the stored verdict always describes the stored text.
 */
@Component
public class LetterGateLoop {

    private static final Logger log = LoggerFactory.getLogger(LetterGateLoop.class);
    private static final int CALLS_PER_ROUND = 1 + LetterGate.MAX_CALLS;

    /** The letter to deliver and the review that describes it. */
    public record Outcome(String body, LetterReview review) {}

    /**
     * The letter and verdict the loop stands on after {@code rounds} rewrites, and why it stops
     * there, if it does.
     */
    private record Round(String body, LetterGateResult verdict, LetterGateStop stop, int rounds) {

        boolean continues() {
            return verdict.verdict() == LetterGateVerdict.FAILED && stop == null;
        }

        Round stoppedBy(LetterGateStop reason) {
            return new Round(body, verdict, reason, rounds);
        }

        Round next(String rewritten, LetterGateResult judged) {
            return new Round(rewritten, judged, null, rounds + 1);
        }
    }

    private final LetterGate gate;
    private final LetterRewriter rewriter;
    private final int maxRounds;
    private final Duration roundReserve;
    private final Clock clock;

    public LetterGateLoop(LetterGate gate, LetterRewriter rewriter,
                          @Value("${app.ai.letter-gate.max-rounds:3}") int maxRounds,
                          @Value("${app.ai.cli.timeout-seconds:120}") long callTimeoutSeconds,
                          Clock clock) {
        this.gate = gate;
        this.rewriter = rewriter;
        this.maxRounds = Math.max(0, maxRounds);
        this.roundReserve = Duration.ofSeconds(callTimeoutSeconds).multipliedBy(CALLS_PER_ROUND);
        this.clock = clock;
    }

    /** Gates {@code draft}, rewriting while it fails, within the rounds and before {@code deadline}. */
    public Outcome run(String draft, List<String> needs, LetterBrief brief, Instant deadline) {
        Round last = rewriteWhileFailing(new Round(draft, gate.judge(draft, brief), null, 0), needs, brief, deadline);
        logVerdict(last);
        LetterGateStop stop = last.stop() != null ? last.stop() : last.verdict().stop();
        LetterGateResult result = last.verdict().after(last.rounds(), stop);
        return new Outcome(last.body(), LetterReview.of(needs, result, last.body()));
    }

    private Round rewriteWhileFailing(Round first, List<String> needs, LetterBrief brief, Instant deadline) {
        Round current = first;
        while (current.continues()) {
            LetterGateStop blocked = blocker(current.rounds(), deadline);
            current = blocked != null ? current.stoppedBy(blocked) : rewriteAndJudge(current, needs, brief);
        }
        return current;
    }

    // Only the last verdict is stored, so the log is the one record of why earlier rounds failed.
    // Point numbers and quote counts only: the quotes are the user's letter.
    private static void logVerdict(Round round) {
        log.info("Letter gate after {} rewrite(s): {}, failed points {}", round.rounds(), round.verdict().verdict(),
                round.verdict().failedPoints().stream().map(p -> p.point() + " (" + p.quotes().size() + " quoted)")
                        .toList());
    }

    /** Why no further round may start, or null when one may. */
    private LetterGateStop blocker(int rounds, Instant deadline) {
        if (rounds >= maxRounds) {
            return LetterGateStop.OUT_OF_ROUNDS;
        }
        return clock.instant().plus(roundReserve).isAfter(deadline) ? LetterGateStop.OUT_OF_TIME : null;
    }

    /**
     * One round. A rewrite that fails, or one the gate gives no verdict on, ends the loop on the
     * letter before it: that letter has a verdict, the new one does not.
     */
    private Round rewriteAndJudge(Round failed, List<String> needs, LetterBrief brief) {
        logVerdict(failed);
        String rewritten;
        try {
            rewritten = rewriter.rewrite(failed.body(), needs, failed.verdict(), brief);
        } catch (RuntimeException e) {
            log.warn("Letter gate: rewrite failed: {}", e.getMessage());
            return failed.stoppedBy(LetterGateStop.REWRITE_FAILED);
        }
        LetterGateResult next = gate.judge(rewritten, brief);
        if (next.verdict() == LetterGateVerdict.NOT_COMPLETED) {
            return failed.stoppedBy(LetterGateStop.REWRITE_NOT_JUDGED);
        }
        return failed.next(rewritten, next);
    }
}
