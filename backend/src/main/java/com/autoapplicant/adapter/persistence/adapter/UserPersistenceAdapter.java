package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.UserMapper;
import com.autoapplicant.adapter.persistence.repository.UserJpaRepository;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository repo;

    public UserPersistenceAdapter(UserJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public User save(User user) {
        return UserMapper.toDomain(repo.save(UserMapper.toEntity(user)));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repo.findById(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repo.findByEmail(email).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByLinkedinId(String linkedinId) {
        return repo.findByLinkedinId(linkedinId).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByFirebaseUid(String firebaseUid) {
        return repo.findByFirebaseUid(firebaseUid).map(UserMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repo.existsByEmail(email);
    }
}
