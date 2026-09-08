package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CareerTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CareerTargetJpaRepository extends JpaRepository<CareerTargetEntity, UUID> {
}
