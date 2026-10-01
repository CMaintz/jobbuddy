package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.UserCompanyNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCompanyNoteJpaRepository
        extends JpaRepository<UserCompanyNoteEntity, UserCompanyNoteEntity.Key> {
}
