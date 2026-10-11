package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Loads the records a document's identity block is built from. They are read server-side and
 * never sent to the AI provider, which is why they travel separately from the career profile.
 */
@Component
public class DocumentOwnerLoader {

    /** Any of user, profile and privateInfo may be null for a half-set-up account. */
    public record DocumentOwner(User user, Profile profile, ProfilePrivateInfo privateInfo,
                                List<ProfileSocial> socials) {}

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final ProfilePrivateInfoRepositoryPort privateInfoRepo;
    private final ProfileSocialRepositoryPort socialRepo;

    public DocumentOwnerLoader(UserRepositoryPort userRepo,
                               ProfileRepositoryPort profileRepo,
                               ProfilePrivateInfoRepositoryPort privateInfoRepo,
                               ProfileSocialRepositoryPort socialRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.privateInfoRepo = privateInfoRepo;
        this.socialRepo = socialRepo;
    }

    public DocumentOwner load(UUID userId) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        ProfilePrivateInfo privateInfo = privateInfoRepo.findByUserId(userId).orElse(null);
        List<ProfileSocial> socials = socialRepo.findByUserId(userId);
        User user = userRepo.findById(userId).orElse(null);
        return new DocumentOwner(user, profile, privateInfo, socials);
    }
}
