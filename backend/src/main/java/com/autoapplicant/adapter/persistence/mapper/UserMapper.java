package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.UserEntity;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;

public final class UserMapper {

    private UserMapper() {}

    public static User toDomain(UserEntity e) {
        return new User(
                e.getId(), e.getEmail(), e.getPasswordHash(), e.getGoogleId(),
                e.getLinkedinId(), e.getFirebaseUid(), UserRole.valueOf(e.getRole()),
                e.isEmailVerified(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    public static UserEntity toEntity(User d) {
        UserEntity e = new UserEntity();
        e.setId(d.id());
        e.setEmail(d.email());
        e.setPasswordHash(d.passwordHash());
        e.setGoogleId(d.googleId());
        e.setLinkedinId(d.linkedinId());
        e.setFirebaseUid(d.firebaseUid());
        e.setRole(d.role().name());
        e.setEmailVerified(d.emailVerified());
        return e;
    }
}
