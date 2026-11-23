package com.autoapplicant.domain.chat;

import com.autoapplicant.domain.application.ApplicationStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * One change in a {@link ChatPlan}. Its state only moves forward: {@code PENDING}, then
 * {@code REJECTED}, or {@code RUNNING} and then {@code DONE} or {@code FAILED}. Only the move out
 * of {@code PENDING} runs anything, so a second click finds the item decided and does nothing.
 *
 * @param details what it changes, as checked when it was proposed; always the type its kind
 *     takes, see {@link Kind#takes(Details)}
 * @param failure why it did not run or did not finish; null unless {@code FAILED}
 * @param resultApplicationId the application its documents were written for, once
 *     {@code GENERATE_DOCUMENTS} is done; null otherwise
 * @param decidedAt when she approved or rejected it
 * @param finishedAt when it was done or failed
 * @throws IllegalArgumentException the details are not the type the kind takes
 */
public record PlanItem(UUID id, Kind kind, Details details, State state, PlanFailure failure,
                       UUID resultApplicationId, Instant decidedAt, Instant finishedAt) {

    public PlanItem {
        Objects.requireNonNull(kind, "kind");
        if (!kind.takes(details)) {
            throw new IllegalArgumentException(kind + " cannot carry " + details);
        }
    }

    public enum Kind {
        SAVE_POSTING,
        /** Hides a posting from her lists; the app also takes it off her saved postings. */
        IGNORE_POSTING,
        UPDATE_STATUS,
        /** Sets the interview time on an application; its deadline is kept. */
        LOG_INTERVIEW,
        /**
         * Writes a tailored CV and cover letter for the posting, on her application for it, for
         * her to review in the app. Runs for minutes, so it finishes in the background.
         */
        GENERATE_DOCUMENTS;

        /** Whether these details are the ones this kind of change is made from. */
        public boolean takes(Details details) {
            return switch (this) {
                case SAVE_POSTING, IGNORE_POSTING -> details instanceof Details.Posting;
                case GENERATE_DOCUMENTS -> details instanceof Details.Documents;
                case UPDATE_STATUS -> details instanceof Details.StatusMove;
                case LOG_INTERVIEW -> details instanceof Details.Interview;
            };
        }
    }

    public enum State { PENDING, RUNNING, DONE, FAILED, REJECTED }

    /**
     * Ids, typed values and the posting's title and company as the database had them, one type
     * per kind of change. Nothing here is the model's free text, so the card she approves says
     * what will really happen. Title and company are null when the posting has none.
     */
    public sealed interface Details {

        UUID postingId();

        String title();

        String company();

        /** Saves or ignores the posting. */
        record Posting(UUID postingId, String title, String company) implements Details {
            public Posting {
                Objects.requireNonNull(postingId, "postingId");
            }
        }

        /** Writes documents for the posting. */
        record Documents(UUID postingId, String title, String company) implements Details {
            public Documents {
                Objects.requireNonNull(postingId, "postingId");
            }
        }

        /**
         * @param fromStatus the application's status when proposed; a different one at approval
         *     means the proposal is stale
         */
        record StatusMove(UUID postingId, UUID applicationId, String title, String company,
                          ApplicationStatus fromStatus, ApplicationStatus toStatus) implements Details {
            public StatusMove {
                Objects.requireNonNull(postingId, "postingId");
                Objects.requireNonNull(applicationId, "applicationId");
                Objects.requireNonNull(fromStatus, "fromStatus");
                Objects.requireNonNull(toStatus, "toStatus");
            }
        }

        /**
         * @param interviewBefore the interview time when proposed, for the same check; null when
         *     none was set
         */
        record Interview(UUID postingId, UUID applicationId, String title, String company,
                         LocalDateTime interviewBefore, LocalDateTime interviewAt) implements Details {
            public Interview {
                Objects.requireNonNull(postingId, "postingId");
                Objects.requireNonNull(applicationId, "applicationId");
                Objects.requireNonNull(interviewAt, "interviewAt");
            }
        }
    }

    public static PlanItem proposed(UUID id, Kind kind, Details details) {
        return new PlanItem(id, kind, details, State.PENDING, null, null, null, null);
    }

    public boolean isPending() {
        return state == State.PENDING;
    }
}
