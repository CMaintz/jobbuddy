package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.ai.AiUsageRecord;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.AiUsageRepositoryPort;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.ai.CurrentUserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the generation provider so every completion a user triggers lands in the
 * usage log. Background work (enrichment, the crawler) has no user to attribute
 * the call to and is not recorded.
 *
 * <p>Logging usage must never cost a generation: a failure to write the record is
 * logged and swallowed.
 */
public class UsageRecordingChatProvider implements ChatProviderPort {

    private static final Logger log = LoggerFactory.getLogger(UsageRecordingChatProvider.class);

    private final ChatProviderPort delegate;
    private final AiUsageRepositoryPort usageRepo;
    private final CurrentUserPort currentUser;

    public UsageRecordingChatProvider(ChatProviderPort delegate,
                                      AiUsageRepositoryPort usageRepo,
                                      CurrentUserPort currentUser) {
        this.delegate = delegate;
        this.usageRepo = usageRepo;
        this.currentUser = currentUser;
    }

    @Override
    public AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation) {
        AiCompletion completion = delegate.complete(composition, jsonObject, operation);
        currentUser.currentUserId().ifPresent(userId -> record(userId, operation, completion));
        return completion;
    }

    private void record(java.util.UUID userId, String operation, AiCompletion completion) {
        try {
            usageRepo.save(new AiUsageRecord(
                    null,
                    userId,
                    completion.model(),
                    completion.tokensIn(),
                    completion.tokensOut(),
                    label(operation),
                    null));
        } catch (RuntimeException e) {
            log.warn("Could not record AI usage for user {}: {}", userId, e.toString());
        }
    }

    /** The column is varchar(50), so an over-long label is trimmed rather than rejected. */
    private static String label(String operation) {
        if (operation == null || operation.isBlank()) return UNLABELLED_OPERATION;
        return operation.length() <= 50 ? operation : operation.substring(0, 50);
    }

    @Override
    public String chatModelName() {
        return delegate.chatModelName();
    }
}
