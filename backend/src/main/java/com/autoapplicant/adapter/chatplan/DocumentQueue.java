package com.autoapplicant.adapter.chatplan;

import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.document.GenerationSettings;
import com.autoapplicant.port.in.chat.DecideChatPlanUseCase;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Writes approved documents one posting at a time, after her request has been answered: each
 * takes minutes of her Claude subscription, and a batch run side by side would compete with the
 * chat and the rest of the app for it. The card shows the item running until it is recorded here.
 */
@Component
@Profile("desktop")
public class DocumentQueue {

    private static final Logger log = LoggerFactory.getLogger(DocumentQueue.class);

    private final DecideChatPlanUseCase decisions;
    private final DocumentGeneration generation;
    private final ApplicationForPosting applications;
    private final Executor executor;

    public DocumentQueue(DecideChatPlanUseCase decisions, DocumentGeneration generation,
                         ApplicationForPosting applications,
                         @Qualifier("chatPlanExecutor") Executor executor) {
        this.decisions = decisions;
        this.generation = generation;
        this.applications = applications;
        this.executor = executor;
    }

    /** Queues an item she has approved, already claimed and so running. */
    void enqueue(UUID userId, UUID planId, PlanItem item, GenerationSettings settings) {
        try {
            executor.execute(() -> write(userId, planId, item, settings));
        } catch (RejectedExecutionException e) {
            log.warn("The document queue is full; chat plan item {} not queued", item.id());
            decisions.finish(userId, planId, item.id(), PlanFailure.FAILED);
        }
    }

    /**
     * Always ends the item, so a card never waits on a running item that nothing will finish;
     * the documents' application is kept even when writing failed partway, since a CV may
     * already be on it.
     */
    private void write(UUID userId, UUID planId, PlanItem item, GenerationSettings settings) {
        UUID postingId = item.details().postingId();
        UUID applicationId = null;
        PlanFailure failure = PlanFailure.FAILED;
        try {
            applicationId = applications.find(userId, postingId);
            generation.write(userId, postingId, settings);
            failure = null;
        } catch (RuntimeException e) {
            failure = failureOf(item, e);
        } finally {
            decisions.finishDocuments(userId, planId, item.id(), applicationId, failure);
        }
    }

    /** A shutdown interrupts the wait for the letter; anything else is a failure. */
    private static PlanFailure failureOf(PlanItem item, RuntimeException e) {
        log.warn("Documents for chat plan item {} could not be written", item.id(), e);
        return Thread.currentThread().isInterrupted() ? PlanFailure.INTERRUPTED : PlanFailure.FAILED;
    }
}
