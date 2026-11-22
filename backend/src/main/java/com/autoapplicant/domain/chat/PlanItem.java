package com.autoapplicant.domain.chat;

import com.autoapplicant.domain.application.ApplicationStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One change in a {@link ChatPlan}. Its state only moves forward: {@code PENDING}, then
 * {@code REJECTED}, or {@code RUNNING} and then {@code DONE} or {@code FAILED}. Only the move out
 * of {@code PENDING} runs anything, so a second click finds the item decided and does nothing.
 *
 * @param details what it changes, as checked when it was proposed
 * @param failure why it did not run or did not finish; null unless {@code FAILED}
 * @param decidedAt when she approved or rejected it
 * @param finishedAt when it was done or failed
 */
public record PlanItem(UUID id, Kind kind, Details details, State state, PlanFailure failure,
                       Instant decidedAt, Instant finishedAt) {

    public enum Kind {
        SAVE_POSTING,
        /** Hides a posting from her lists; the app also takes it off her saved postings. */
        IGNORE_POSTING,
        UPDATE_STATUS,
        /** Sets the interview time on an application; its deadline is kept. */
        LOG_INTERVIEW
    }

    public enum State { PENDING, RUNNING, DONE, FAILED, REJECTED }

    /**
     * Ids, typed values and the posting's title and company as the database had them. Nothing
     * here is the model's free text, so the card she approves says what will really happen.
     *
     * @param fromStatus the application's status when proposed; a different one at approval
     *     means the proposal is stale
     * @param interviewBefore the interview time when proposed, for the same check
     */
    public record Details(UUID postingId, UUID applicationId, String title, String company,
                          ApplicationStatus fromStatus, ApplicationStatus toStatus,
                          LocalDateTime interviewBefore, LocalDateTime interviewAt) {

        public static Details posting(UUID postingId, String title, String company) {
            return new Details(postingId, null, title, company, null, null, null, null);
        }
    }

    public static PlanItem proposed(UUID id, Kind kind, Details details) {
        return new PlanItem(id, kind, details, State.PENDING, null, null, null);
    }

    public boolean isPending() {
        return state == State.PENDING;
    }
}
