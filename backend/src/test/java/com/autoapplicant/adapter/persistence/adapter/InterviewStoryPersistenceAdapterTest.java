package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.persistence.entity.InterviewStoryEntity;
import com.autoapplicant.adapter.persistence.repository.InterviewStoryJpaRepository;
import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.domain.user.InterviewStory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * User B knows the id of one of user A's stories. Saving with that id must not overwrite or take
 * over A's story; it has to look exactly like a story that does not exist.
 */
class InterviewStoryPersistenceAdapterTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID STORY = UUID.randomUUID();

    private final InterviewStoryJpaRepository repo = mock(InterviewStoryJpaRepository.class);
    private final InterviewStoryPersistenceAdapter adapter = new InterviewStoryPersistenceAdapter(repo);

    @Test
    void another_users_story_cannot_be_overwritten() {
        when(repo.findByIdAndUserId(STORY, BOB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.save(story(STORY, BOB, "Hijacked")))
                .isInstanceOf(NotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void the_owner_updates_the_same_row() {
        InterviewStoryEntity existing = entity(STORY, ALICE, "Original");
        when(repo.findByIdAndUserId(STORY, ALICE)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewStory saved = adapter.save(story(STORY, ALICE, "Edited"));

        assertThat(saved.id()).isEqualTo(STORY);
        assertThat(saved.userId()).isEqualTo(ALICE);
        assertThat(saved.title()).isEqualTo("Edited");
    }

    @Test
    void a_story_without_id_is_created_for_the_caller() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewStory saved = adapter.save(story(null, BOB, "New"));

        assertThat(saved.userId()).isEqualTo(BOB);
        verify(repo, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void delete_is_scoped_to_the_caller() {
        adapter.delete(STORY, BOB);

        verify(repo).deleteByIdAndUserId(STORY, BOB);
    }

    private static InterviewStory story(UUID id, UUID owner, String title) {
        return new InterviewStory(id, owner, title, null, null, null, null, null, List.of(), null, null);
    }

    private static InterviewStoryEntity entity(UUID id, UUID owner, String title) {
        InterviewStoryEntity e = new InterviewStoryEntity();
        e.setId(id);
        e.setUserId(owner);
        e.setTitle(title);
        return e;
    }
}
