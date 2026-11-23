package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A user has one writing profile, and the PUT body echoes back an id. Bob sending Alice's
 * profile id must not overwrite or take over Alice's row.
 */
class WritingProfileServiceTest {

    private static final UUID ALICE_PROFILE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID BOB_PROFILE = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2025-01-01T00:00:00Z");

    private final WritingProfileRepositoryPort repo = mock(WritingProfileRepositoryPort.class);
    private final WritingProfileService service = new WritingProfileService(repo,
            mock(GeneratedDocumentRepositoryPort.class), mock(ChatProviderPort.class),
            new ObjectMapper());

    private static WritingProfile profile(UUID id, UUID owner, String tone, Instant createdAt) {
        return new WritingProfile(id, owner, tone, null, List.of(), List.of(), List.of(),
                List.of(), null, null, createdAt, null);
    }

    private WritingProfile savedProfile() {
        ArgumentCaptor<WritingProfile> captor = ArgumentCaptor.forClass(WritingProfile.class);
        verify(repo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void another_users_profile_id_in_the_body_does_not_touch_their_row() {
        when(repo.findByUserId(BOB)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.save(profile(ALICE_PROFILE, BOB, "Hijacked", null));

        WritingProfile saved = savedProfile();
        assertThat(saved.id()).isNull();
        assertThat(saved.userId()).isEqualTo(BOB);
    }

    @Test
    void the_owner_updates_their_own_row_whatever_id_the_body_carries() {
        when(repo.findByUserId(BOB)).thenReturn(Optional.of(profile(BOB_PROFILE, BOB, "Dry", CREATED)));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.save(profile(ALICE_PROFILE, BOB, "Warm", null));

        WritingProfile saved = savedProfile();
        assertThat(saved.id()).isEqualTo(BOB_PROFILE);
        assertThat(saved.tone()).isEqualTo("Warm");
        assertThat(saved.createdAt()).isEqualTo(CREATED);
    }
}
