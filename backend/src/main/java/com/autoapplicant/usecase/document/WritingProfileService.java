package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.in.document.ManageWritingProfileUseCase;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// TODO(writing-style-analysis): derive the profile from writing samples instead of
// hand-typing it. Planned shape: POST /users/me/writing-style/analyze {samples: [string]}
// — user pastes 1-3 texts they actually wrote (old cover letters, emails); one AI call
// returns a PROPOSED WritingProfile (tone, vocabularyNotes, phrasingPatterns, dos, donts,
// structureNotes, exampleExcerpts trimmed from the samples) which is NOT persisted —
// the settings screen prefills the Writing style form with it, the user reviews/tweaks
// and saves via the existing PUT, which is when lastAnalyzedAt gets set (the column has
// existed since the original schema for exactly this feature; nothing writes it today).
// Needs: new port/in method (AnalyzeWritingStyleUseCase), generationAiProvider injected
// here, JSON-schema prompt, an "Analyze my writing" modal in settings. Samples are
// user-supplied text sent to the AI as-is (same trust model as CV analysis) — consider
// stripping obvious contact lines first.
@Service
public class WritingProfileService implements ManageWritingProfileUseCase {

    private final WritingProfileRepositoryPort repo;

    public WritingProfileService(WritingProfileRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Optional<WritingProfile> get(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public WritingProfile save(WritingProfile profile) {
        return repo.save(profile);
    }
}
