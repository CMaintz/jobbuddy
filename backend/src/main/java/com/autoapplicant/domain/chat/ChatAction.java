package com.autoapplicant.domain.chat;

import java.util.List;
import java.util.UUID;

/**
 * A button the chat offers her. The model can only propose one through a tool; the server checks
 * it and the app renders it with its own label. Nothing happens until she clicks it.
 *
 * @param postings the postings it is about, in order; their titles come from the database, never
 *     from the model, and are null until the action is resolved
 */
public record ChatAction(Kind kind, List<Posting> postings) {

    public enum Kind {
        /** Opens one posting. */
        OPEN_POSTING,
        /** Starts an application for one posting. */
        START_APPLICATION,
        /** Opens her pipeline. */
        SHOW_PIPELINE,
        /** Walks through several postings in order, one at a time. */
        STEP_THROUGH
    }

    public record Posting(UUID id, String title, String company) {

        public static Posting unresolved(UUID id) {
            return new Posting(id, null, null);
        }
    }

    public ChatAction {
        postings = List.copyOf(postings);
    }

    public List<UUID> postingIds() {
        return postings.stream().map(Posting::id).toList();
    }
}
