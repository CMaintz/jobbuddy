package com.autoapplicant.port.in.chat;

import com.autoapplicant.domain.chat.ChatAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Checks a button the chat model proposes before the app may show it. */
public interface ResolveChatActionUseCase {

    /**
     * The action with its postings' titles from the database, or empty when it does not fit its
     * kind (one posting to open, none for the pipeline, up to ten to step through) or names a
     * posting that does not exist.
     */
    Optional<ChatAction> resolve(ChatAction.Kind kind, List<UUID> postingIds);
}
