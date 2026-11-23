package com.autoapplicant.domain.chat;

/** Why an approved {@link PlanItem} did not run or did not finish. The UI words each one. */
public enum PlanFailure {
    /** The posting no longer exists. */
    POSTING_GONE,
    /** The application no longer exists, or is not hers. */
    APPLICATION_GONE,
    /** What the proposal was based on has changed since; she should ask again. */
    CHANGED_SINCE,
    /** The app no longer allows the change, such as a status move it refuses. */
    NOT_ALLOWED,
    /** The app stopped while it ran. */
    INTERRUPTED,
    /** Anything else; the details are in the server log. */
    FAILED
}
