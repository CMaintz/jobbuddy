package com.autoapplicant.usecase.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.skills.AdjacentSkillRunRepositoryPort;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillCandidateDismissalRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class AdjacentSkillSuggesterTest {

    private static final UUID USER = UUID.randomUUID();
    /** parsed_skill_suggestion.skill_name is varchar(200). */
    private static final int SKILL_NAME_COLUMN = 200;

    private final List<String> held = new ArrayList<>(List.of("Kampagneplanlægning", "SEO"));
    private final List<ParsedSkillSuggestion> queued = new ArrayList<>();
    private final Set<String> declined = new HashSet<>();
    private final Map<UUID, String> runs = new HashMap<>();
    private final List<String> prompts = new CopyOnWriteArrayList<>();
    /** When set, the AI holds each answer until it is counted down. */
    private CountDownLatch answerHeld;
    private String reply = answer("Content marketing", "Kampagneplanlægning");

    private final SkillTaxonomyRepositoryPort taxonomy = mock(SkillTaxonomyRepositoryPort.class);
    private final ProfileSkillRepositoryPort skills = mock(ProfileSkillRepositoryPort.class);

    {
        when(skills.findByUserId(USER)).thenAnswer(call -> held.stream()
                .map(name -> new ProfileSkill(UUID.randomUUID(), USER, name, null, null, null,
                        false, 0, null)).toList());
    }

    private final AdjacentSkillSuggester suggester = new AdjacentSkillSuggester(skills,
            queue(), dismissals(), runStore(), ai(),
            new SkillResolver(taxonomy, new SkillCanonicalizer(taxonomy)), new ObjectMapper());

    @Test
    void a_suggestion_is_queued_with_the_held_skill_it_follows_from() {
        suggester.suggest(USER);

        assertThat(queued).singleElement().satisfies(s -> {
            assertThat(s.skillName()).isEqualTo("Content marketing");
            assertThat(s.evidence()).isEqualTo("Kampagneplanlægning");
            assertThat(s.source()).isEqualTo(ParsedSkillSuggestion.Source.ADJACENT);
        });
    }

    @Test
    void a_suggestion_following_from_a_skill_the_user_never_listed_is_dropped() {
        reply = answer("Crisis communication", "Pressearbejde");

        suggester.suggest(USER);

        assertThat(queued).isEmpty();
    }

    @Test
    void a_skill_already_held_or_declined_is_not_asked_about() {
        declined.add("content marketing");
        reply = """
                {"suggestions": [{"skill": "Content marketing", "because": "Kampagneplanlægning"},
                                 {"skill": "seo", "because": "Kampagneplanlægning"}]}""";

        suggester.suggest(USER);

        assertThat(queued).isEmpty();
    }

    @Test
    void saving_a_profile_whose_skills_did_not_change_asks_the_ai_nothing() {
        suggester.suggest(USER);
        suggester.suggest(USER);

        assertThat(prompts).hasSize(1);
    }

    @Test
    void a_changed_skill_set_is_asked_about_again() {
        suggester.suggest(USER);
        held.add("Pressearbejde");

        suggester.suggest(USER);

        assertThat(prompts).hasSize(2);
    }

    @Test
    void a_failed_ai_call_is_retried_on_the_next_save() {
        reply = null;
        assertThatThrownBy(() -> suggester.suggest(USER)).isInstanceOf(IllegalStateException.class);
        reply = answer("Content marketing", "Kampagneplanlægning");

        suggester.suggest(USER);

        assertThat(queued).hasSize(1);
    }

    @Test
    void an_overlong_skill_is_dropped_and_the_rest_are_queued_and_remembered() {
        reply = "{\"suggestions\": [{\"skill\": \"" + "x".repeat(300)
                + "\", \"because\": \"SEO\"},"
                + " {\"skill\": \"Søgeordsanalyse\", \"because\": \"SEO\"}]}";

        suggester.suggest(USER);
        suggester.suggest(USER);

        assertThat(queued).extracting(ParsedSkillSuggestion::skillName)
                .containsExactly("Søgeordsanalyse");
        assertThat(prompts).hasSize(1);
    }

    @Test
    void an_unreadable_reply_is_asked_again_on_the_next_save() {
        reply = "Sorry, I cannot help with that.";

        suggester.suggest(USER);
        suggester.suggest(USER);

        assertThat(prompts).hasSize(2);
    }

    @Test
    void a_readable_empty_answer_is_remembered() {
        reply = "{\"suggestions\": []}";

        suggester.suggest(USER);
        suggester.suggest(USER);

        assertThat(prompts).hasSize(1);
    }

    @Test
    void a_reply_that_corrects_itself_is_read_by_its_correction() {
        reply = answer("Softwareentwicklung", "SEO")
                + "\n\nWait, the skills are in Danish. Corrected:\n\n"
                + answer("Søgeordsanalyse", "SEO");

        suggester.suggest(USER);

        assertThat(queued).extracting(ParsedSkillSuggestion::skillName)
                .containsExactly("Søgeordsanalyse");
    }

    @Test
    void an_import_and_the_save_right_after_it_ask_the_ai_once() throws Exception {
        answerHeld = new CountDownLatch(1);
        Thread importRun = new Thread(() -> suggester.suggest(USER));
        Thread saveRun = new Thread(() -> suggester.suggest(USER));

        importRun.start();
        waitFor(() -> prompts.size() == 1);
        saveRun.start();
        waitFor(() -> prompts.size() == 2 || saveRun.getState() == Thread.State.WAITING);
        answerHeld.countDown();
        importRun.join();
        saveRun.join();

        assertThat(prompts).hasSize(1);
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private void awaitRelease() {
        if (answerHeld == null) return;
        try {
            answerHeld.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String answer(String skill, String because) {
        return "{\"suggestions\": [{\"skill\": \"" + skill + "\", \"because\": \"" + because + "\"}]}";
    }

    private ChatProviderPort ai() {
        return new ChatProviderPort() {
            @Override
            public AiCompletion complete(PromptComposition composition, boolean jsonObject,
                                         String operation) {
                prompts.add(composition.resolvedFinalPrompt());
                awaitRelease();
                if (reply == null) throw new IllegalStateException("CLI agent exited 1");
                return AiCompletion.untracked(reply, "fake");
            }

            @Override
            public String chatModelName() {
                return "fake";
            }
        };
    }

    private ParsedSkillSuggestionRepositoryPort queue() {
        return new ParsedSkillSuggestionRepositoryPort() {
            @Override public List<ParsedSkillSuggestion> findByUserId(UUID userId) {
                return List.copyOf(queued);
            }
            @Override public void record(ParsedSkillSuggestion suggestion) {
                if (suggestion.skillName().length() > SKILL_NAME_COLUMN) {
                    throw new IllegalArgumentException("value too long for varchar(200)");
                }
                queued.add(suggestion);
            }
            @Override public void remove(UUID userId, String normalizedName) {
                queued.removeIf(s -> s.normalizedName().equals(normalizedName));
            }
        };
    }

    private SkillCandidateDismissalRepositoryPort dismissals() {
        return new SkillCandidateDismissalRepositoryPort() {
            @Override public Set<String> findDismissedNames(UUID userId) {
                return declined;
            }
            @Override public void dismiss(UUID userId, String normalizedName) {
                declined.add(normalizedName);
            }
        };
    }

    private AdjacentSkillRunRepositoryPort runStore() {
        return new AdjacentSkillRunRepositoryPort() {
            @Override public Optional<String> lastFingerprint(UUID userId) {
                return Optional.ofNullable(runs.get(userId));
            }
            @Override public void record(UUID userId, String fingerprint) {
                runs.put(userId, fingerprint);
            }
        };
    }
}
