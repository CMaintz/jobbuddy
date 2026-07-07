package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.port.in.user.ManageProfilePrivateInfoUseCase;
import com.autoapplicant.port.out.storage.FileStoragePort;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProfilePrivateInfoService implements ManageProfilePrivateInfoUseCase {

    private final ProfilePrivateInfoRepositoryPort repo;
    private final FileStoragePort fileStorage;

    public ProfilePrivateInfoService(ProfilePrivateInfoRepositoryPort repo, FileStoragePort fileStorage) {
        this.repo = repo;
        this.fileStorage = fileStorage;
    }

    @Override
    public ProfilePrivateInfo getPrivateInfo(UUID userId) {
        return repo.findByUserId(userId)
                .orElse(new ProfilePrivateInfo(null, userId, null, null, null, null, null, null, null, null));
    }

    @Override
    public ProfilePrivateInfo updatePrivateInfo(UUID userId, ProfilePrivateInfo info) {
        // Merge semantics: null means "keep existing", so clients can send partial updates.
        // Fields are cleared by sending an empty string, not null.
        ProfilePrivateInfo existing = getPrivateInfo(userId);
        ProfilePrivateInfo toSave = new ProfilePrivateInfo(
                existing.id(), userId,
                coalesce(info.fullName(), existing.fullName()),
                coalesce(info.phone(), existing.phone()),
                coalesce(info.photoUrl(), existing.photoUrl()),
                coalesce(info.location(), existing.location()),
                coalesce(info.municipality(), existing.municipality()),
                coalesce(info.contactEmail(), existing.contactEmail()),
                null, null);
        return repo.save(toSave);
    }

    private static String coalesce(String value, String fallback) {
        return value != null ? value : fallback;
    }

    @Override
    public ProfilePrivateInfo updatePhotoUrl(UUID userId, String photoUrl) {
        ProfilePrivateInfo existing = getPrivateInfo(userId);
        ProfilePrivateInfo updated = new ProfilePrivateInfo(
                existing.id(), userId, existing.fullName(), existing.phone(),
                photoUrl, existing.location(), existing.municipality(),
                existing.contactEmail(), null, null);
        return repo.save(updated);
    }

    @Override
    public String uploadPhoto(UUID userId, byte[] bytes, String originalFilename) {
        String ext = resolveExtension(originalFilename);
        String filename = userId + "." + ext;
        String url = fileStorage.store(bytes, "uploads/profile-photos", filename);
        updatePhotoUrl(userId, url);
        return url;
    }

    private static String resolveExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return ext.matches("jpg|jpeg|png|webp|gif") ? ext : "jpg";
    }
}
