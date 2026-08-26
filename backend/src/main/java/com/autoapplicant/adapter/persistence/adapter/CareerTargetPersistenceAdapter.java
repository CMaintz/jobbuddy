package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.CareerTargetEntity;
import com.autoapplicant.adapter.persistence.repository.CareerTargetJpaRepository;
import com.autoapplicant.domain.user.CareerStage;
import com.autoapplicant.domain.user.CareerTarget;
import com.autoapplicant.port.out.user.CareerTargetRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CareerTargetPersistenceAdapter implements CareerTargetRepositoryPort {

    private final CareerTargetJpaRepository repo;

    public CareerTargetPersistenceAdapter(CareerTargetJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<CareerTarget> findByUserId(UUID userId) {
        return repo.findById(userId).map(this::toDomain);
    }

    @Override
    public CareerTarget save(CareerTarget target) {
        CareerTargetEntity e = repo.findById(target.userId()).orElseGet(CareerTargetEntity::new);
        e.setUserId(target.userId());
        e.setTargetArchetypes(toArray(target.targetArchetypes()));
        e.setNorthStar(target.northStar());
        e.setNarrative(target.narrative());
        e.setCultureRequirements(toArray(target.cultureRequirements()));
        e.setCareerStage(target.careerStage() != null ? target.careerStage().name() : null);
        e.setNoticePeriod(target.noticePeriod());
        e.setEarliestStartDate(target.earliestStartDate());
        return toDomain(repo.save(e));
    }

    private CareerTarget toDomain(CareerTargetEntity e) {
        return new CareerTarget(e.getUserId(), toList(e.getTargetArchetypes()), e.getNorthStar(),
                e.getNarrative(), toList(e.getCultureRequirements()), parseStage(e.getCareerStage()),
                e.getNoticePeriod(), e.getEarliestStartDate(), e.getUpdatedAt());
    }

    private static CareerStage parseStage(String value) {
        if (value == null || value.isBlank()) return null;
        try { return CareerStage.valueOf(value); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private static List<String> toList(String[] arr) { return arr != null ? Arrays.asList(arr) : List.of(); }
    private static String[] toArray(List<String> l) { return l != null ? l.toArray(String[]::new) : new String[0]; }
}
