package com.autoapplicant.domain.chat;

/**
 * The chat model proposed something that cannot become a proposal: a posting or application
 * that is not there or not hers, or a change the app does not allow. The message is for the
 * model, so it can correct itself; she never sees the refused proposal.
 */
public class ProposalRefusedException extends RuntimeException {

    public ProposalRefusedException(String message) {
        super(message);
    }
}
