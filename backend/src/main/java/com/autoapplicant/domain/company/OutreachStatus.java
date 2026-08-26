package com.autoapplicant.domain.company;

/**
 * Where a tracked unsolicited outreach has got to.
 *
 * <p>Deliberately shorter than the application pipeline: there is no posting, no screening and no
 * offer stage here. Once outreach turns into a real application it belongs in `applications`.
 */
public enum OutreachStatus {
    /** On the list, not yet contacted. */
    SAVED,
    /** Letter or message sent; the follow-up clock starts here. */
    CONTACTED,
    /** They answered — whatever the answer was. */
    REPLIED,
    /** A conversation is booked or has happened. */
    MEETING,
    /** Nothing further to do: declined, went quiet, or became a real application. */
    CLOSED
}
