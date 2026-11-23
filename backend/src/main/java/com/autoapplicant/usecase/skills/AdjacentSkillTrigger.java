package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.user.ProfileSavedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Asks for adjacent-skill suggestions after a profile save or a CV import, off the request
 * thread: the AI call takes seconds, and a failed one must never fail the save.
 */
@Component
public class AdjacentSkillTrigger {

    private static final Logger log = LoggerFactory.getLogger(AdjacentSkillTrigger.class);

    private final AdjacentSkillSuggester suggester;

    public AdjacentSkillTrigger(AdjacentSkillSuggester suggester) {
        this.suggester = suggester;
    }

    @Async("userAiTaskExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onProfileSaved(ProfileSavedEvent event) {
        try {
            suggester.suggest(event.userId());
        } catch (RuntimeException e) {
            log.warn("Could not suggest adjacent skills for user {}: {}",
                    event.userId(), e.getMessage());
        }
    }
}
