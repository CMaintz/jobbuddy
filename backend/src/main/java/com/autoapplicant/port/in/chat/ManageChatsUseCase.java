package com.autoapplicant.port.in.chat;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatTranscript;
import java.util.List;
import java.util.UUID;

/**
 * The user's saved chats. Every method takes the caller's id and treats someone else's chat as
 * missing.
 *
 * @throws com.autoapplicant.domain.common.NotFoundException from the per-chat methods when the
 *     chat does not exist or is not hers
 */
public interface ManageChatsUseCase {

    List<Chat> list(UUID userId);

    Chat create(UUID userId);

    Chat rename(UUID userId, UUID chatId, String title);

    void delete(UUID userId, UUID chatId);

    ChatTranscript messages(UUID userId, UUID chatId);
}
