package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.skills.AdjacentSkillRunRepositoryPort;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillCandidateDismissalRepositoryPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Asks the AI which skills a profile very likely holds without listing them, and queues each
 * as a question for the user ("Du har Kampagneplanlægning. Har du også Content marketing?").
 *
 * <p>{@link AdjacentSkillTrigger} runs it off the request thread after a profile save or a CV
 * import. The answers go into the same queue as the CV parser's inferences, never into the
 * profile: only the user's yes makes a skill theirs, and until then it is no evidence for a CV, a
 * letter or the fact gate. Matching alone gives a queued suggestion a little credit, since the
 * user very likely has it.
 *
 * <p>A save that leaves the skills as they were asks nothing, so editing a headline costs no AI
 * call. The skill set is remembered only once the AI has given a readable answer, so a failed
 * call or an unreadable reply is retried on the next save.
 */
@Component
public class AdjacentSkillSuggester {

    /**
     * A CV import and the save that applies its preview arrive seconds apart, both while the
     * first AI call is still running. Serialising them per user lets the second see the first's
     * remembered skill set instead of asking the AI again. Striped so the set stays bounded; a
     * collision only makes two users wait on each other.
     */
    private static final int LOCK_STRIPES = 64;
    private final ReentrantLock[] userLocks =
            Stream.generate(ReentrantLock::new).limit(LOCK_STRIPES).toArray(ReentrantLock[]::new);

    private final ProfileSkillRepositoryPort skills;
    private final ParsedSkillSuggestionRepositoryPort queue;
    private final SkillCandidateDismissalRepositoryPort dismissals;
    private final AdjacentSkillRunRepositoryPort runs;
    private final ChatProviderPort ai;
    private final SkillResolver resolver;
    private final ObjectMapper json;

    public AdjacentSkillSuggester(ProfileSkillRepositoryPort skills,
                                  ParsedSkillSuggestionRepositoryPort queue,
                                  SkillCandidateDismissalRepositoryPort dismissals,
                                  AdjacentSkillRunRepositoryPort runs,
                                  @Qualifier("generationAiProvider") ChatProviderPort ai,
                                  SkillResolver resolver, ObjectMapper json) {
        this.skills = skills;
        this.queue = queue;
        this.dismissals = dismissals;
        this.runs = runs;
        this.ai = ai;
        this.resolver = resolver;
        this.json = json;
    }

    /**
     * Queues new suggestions for the user's current skills, unless they were already asked.
     *
     * @throws RuntimeException when the AI call fails; nothing is remembered, so the next save
     *     asks again, as it does after a reply it cannot read
     */
    void suggest(UUID userId) {
        ReentrantLock lock = userLocks[Math.floorMod(userId.hashCode(), LOCK_STRIPES)];
        lock.lock();
        try {
            suggestNow(userId);
        } finally {
            lock.unlock();
        }
    }

    private void suggestNow(UUID userId) {
        List<String> held = skills.findByUserId(userId).stream().map(ProfileSkill::skillName)
                .filter(Objects::nonNull).filter(name -> !name.isBlank()).toList();
        if (held.isEmpty()) return;
        String fingerprint = fingerprint(held);
        if (runs.lastFingerprint(userId).filter(fingerprint::equals).isPresent()) return;
        askAi(held).ifPresent(answer -> {
            newQuestions(userId, held, answer).forEach(queue::record);
            runs.record(userId, fingerprint);
        });
    }

    /** The AI's suggestions, or empty when its reply could not be read. */
    private Optional<List<AdjacentSkillReply.Suggestion>> askAi(List<String> held) {
        String reply = ai.generateJson(AdjacentSkillPrompt.forSkills(held),
                AiOperations.ADJACENT_SKILLS);
        return AdjacentSkillReply.parse(json, reply);
    }

    /**
     * The suggestions that name a skill the user does not hold, has not declined, and follow from
     * one they do hold: a question quoting a skill they never listed is nonsense. All of them are
     * settled before any is filed, so a bad entry cannot leave half a run queued.
     */
    private List<ParsedSkillSuggestion> newQuestions(
            UUID userId, List<String> held, List<AdjacentSkillReply.Suggestion> suggestions) {
        Map<String, String> anchors = new HashMap<>();
        held.forEach(name -> anchors.put(SkillNames.normalize(name), name));
        Set<String> declined = dismissals.findDismissedNames(userId);
        Set<String> taken = new HashSet<>();
        held.forEach(name -> taken.add(resolver.key(name)));
        List<ParsedSkillSuggestion> questions = new ArrayList<>();
        suggestions.stream().limit(AdjacentSkillPrompt.MAX_SUGGESTIONS).forEach(s -> {
            String anchor = anchors.get(SkillNames.normalize(s.because()));
            String normalized = SkillNames.normalize(s.skill());
            if (anchor == null || declined.contains(normalized)
                    || !taken.add(resolver.key(s.skill()))) return;
            questions.add(new ParsedSkillSuggestion(null, userId, s.skill(), normalized, anchor,
                    ParsedSkillSuggestion.Source.ADJACENT));
        });
        return questions;
    }

    /** The skill set, independent of order and of spelling variants of one skill. */
    private String fingerprint(List<String> held) {
        String keys = String.join("\n",
                held.stream().map(resolver::key).sorted().distinct().toList());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(keys.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JDK", e);
        }
    }
}
