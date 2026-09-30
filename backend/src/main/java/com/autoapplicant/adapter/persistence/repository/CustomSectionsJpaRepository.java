package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CustomSectionsEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by user_id (the natural primary key); findById(userId) is the per-user lookup. */
public interface CustomSectionsJpaRepository extends JpaRepository<CustomSectionsEntity, UUID> {
}
