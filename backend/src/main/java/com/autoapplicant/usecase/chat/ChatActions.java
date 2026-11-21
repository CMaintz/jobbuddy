package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.chat.ChatAction.Kind;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns what the model proposed into a button the app can show, or nothing. The model's choice
 * is only a kind and posting ids: titles are read from the database, and a posting that does not
 * exist (a made-up or stale id) never becomes a button.
 */
final class ChatActions {

    private final JobRepositoryPort jobs;

    ChatActions(JobRepositoryPort jobs) {
        this.jobs = jobs;
    }

    Optional<ChatAction> resolve(Kind kind, List<UUID> postingIds) {
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(postingIds));
        if (!fitsKind(kind, ids.size())) {
            return Optional.empty();
        }
        Map<UUID, Job> found = jobs.findByIds(ids).stream()
                .collect(Collectors.toMap(Job::id, Function.identity(), (a, b) -> a));
        List<ChatAction.Posting> postings = ids.stream()
                .filter(found::containsKey)
                .map(id -> new ChatAction.Posting(id, found.get(id).title(),
                        found.get(id).companyName()))
                .toList();
        return postings.size() == ids.size()
                ? Optional.of(new ChatAction(kind, postings))
                : Optional.empty();
    }

    private static boolean fitsKind(Kind kind, int count) {
        return switch (kind) {
            case OPEN_POSTING, START_APPLICATION -> count == 1;
            case SHOW_PIPELINE -> count == 0;
            case STEP_THROUGH -> count >= 1 && count <= ChatAction.MAX_STEP_THROUGH;
        };
    }
}
