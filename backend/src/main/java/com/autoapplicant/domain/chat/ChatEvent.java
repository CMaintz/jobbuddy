package com.autoapplicant.domain.chat;

/**
 * What a running chat turn reports, in order: any number of {@link Delta}s, {@link Action}s and
 * {@link Proposed}s, then exactly one of
 * {@link Completed}, {@link Stopped} or {@link Failed}.
 */
public sealed interface ChatEvent {

    /** The next piece of the answer as the model writes it. */
    record Delta(String text) implements ChatEvent {}

    /** A button the chat offers her, already checked by the server. */
    record Action(ChatAction action) implements ChatEvent {}

    /** Changes the chat proposed, stored and waiting for her to approve or reject them. */
    record Proposed(ChatPlan plan) implements ChatEvent {}

    record Completed() implements ChatEvent {}

    /** The user stopped the turn, or her connection went away. */
    record Stopped() implements ChatEvent {}

    record Failed(ChatFailure failure) implements ChatEvent {}

    /** True for the event that ends a turn. */
    default boolean isTerminal() {
        return !(this instanceof Delta || this instanceof Action || this instanceof Proposed);
    }
}
