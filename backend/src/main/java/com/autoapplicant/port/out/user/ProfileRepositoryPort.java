package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.Profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepositoryPort {
    Profile save(Profile profile);
    Optional<Profile> findByUserId(UUID userId);
    List<Profile> findAll();
}
