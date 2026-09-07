package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.ProfileStrength;
import com.autoapplicant.port.in.user.ManageProfileStrengthUseCase;
import com.autoapplicant.port.out.user.ProfileStrengthRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileStrengthService implements ManageProfileStrengthUseCase {

    private final ProfileStrengthRepositoryPort repo;

    public ProfileStrengthService(ProfileStrengthRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<ProfileStrength> getStrengths(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public ProfileStrength addStrength(UUID userId, ProfileStrength strength) {
        ProfileStrength toSave = new ProfileStrength(null, userId, strength.title(),
                strength.description(), strength.iconKey(), strength.displayOrder(), null, null);
        return repo.save(toSave);
    }

    @Override
    public ProfileStrength updateStrength(UUID userId, UUID id, ProfileStrength strength) {
        ProfileStrength toSave = new ProfileStrength(id, userId, strength.title(),
                strength.description(), strength.iconKey(), strength.displayOrder(), null, null);
        return repo.save(toSave);
    }

    @Override
    public void deleteStrength(UUID userId, UUID id) {
        repo.deleteByIdAndUserId(id, userId);
    }

    @Override
    public List<ProfileStrength> reorderStrengths(UUID userId, List<ProfileStrength> ordered) {
        for (ProfileStrength s : ordered) {
            repo.findByIdAndUserId(s.id(), userId).ifPresent(existing -> {
                repo.save(new ProfileStrength(existing.id(), userId, existing.title(),
                        existing.description(), existing.iconKey(), s.displayOrder(), null, null));
            });
        }
        return repo.findByUserId(userId);
    }
}
