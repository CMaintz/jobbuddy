package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.port.in.user.ManageProfileSocialUseCase;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileSocialService implements ManageProfileSocialUseCase {

    private final ProfileSocialRepositoryPort repo;

    public ProfileSocialService(ProfileSocialRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<ProfileSocial> getSocials(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public ProfileSocial addSocial(UUID userId, ProfileSocial social) {
        ProfileSocial toSave = new ProfileSocial(null, userId, social.platform(),
                social.url(), social.username(), social.iconKey(), social.displayOrder(), null, null);
        return repo.save(toSave);
    }

    @Override
    public ProfileSocial updateSocial(UUID userId, UUID id, ProfileSocial social) {
        ProfileSocial toSave = new ProfileSocial(id, userId, social.platform(),
                social.url(), social.username(), social.iconKey(), social.displayOrder(), null, null);
        return repo.save(toSave);
    }

    @Override
    public void deleteSocial(UUID userId, UUID id) {
        repo.deleteByIdAndUserId(id, userId);
    }

    @Override
    public List<ProfileSocial> reorderSocials(UUID userId, List<ProfileSocial> ordered) {
        for (ProfileSocial s : ordered) {
            repo.findByIdAndUserId(s.id(), userId).ifPresent(existing -> {
                repo.save(new ProfileSocial(existing.id(), userId, existing.platform(),
                        existing.url(), existing.username(), existing.iconKey(),
                        s.displayOrder(), null, null));
            });
        }
        return repo.findByUserId(userId);
    }
}
