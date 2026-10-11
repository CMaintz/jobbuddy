package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(UUID id);
    /** Case-insensitive, since providers report the same address in different cases. */
    Optional<User> findByEmail(String email);
    Optional<User> findByLinkedinId(String linkedinId);
    Optional<User> findByFirebaseUid(String firebaseUid);
    boolean existsByEmail(String email);
    void deleteById(UUID id);
}
