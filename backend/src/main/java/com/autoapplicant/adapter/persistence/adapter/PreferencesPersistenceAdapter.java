package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.PreferencesEntity;
import com.autoapplicant.adapter.persistence.repository.PreferencesJpaRepository;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PreferencesPersistenceAdapter implements PreferencesRepositoryPort {

    private final PreferencesJpaRepository repo;

    public PreferencesPersistenceAdapter(PreferencesJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserPreferences save(UserPreferences prefs) {
        PreferencesEntity e = repo.findByUserId(prefs.userId()).orElse(new PreferencesEntity());
        e.setUserId(prefs.userId());
        e.setPreferredLocations(toArray(prefs.preferredLocations()));
        e.setPreferredMunicipalities(toArray(prefs.preferredMunicipalities()));
        e.setPositiveSignals(toArray(prefs.positiveSignals()));
        e.setNegativeSignals(toArray(prefs.negativeSignals()));
        e.setExcludedCompanies(toArray(prefs.excludedCompanies()));
        e.setPreferredRemoteTypes(toArray(prefs.preferredRemoteTypes()));
        e.setPreferredEmploymentTypes(toArray(prefs.preferredEmploymentTypes()));
        e.setPreferredSeniority(toArray(prefs.preferredSeniority()));
        e.setSalaryMin(prefs.salaryMin());
        e.setSalaryMax(prefs.salaryMax());
        e.setMaxCommuteKm(prefs.maxCommuteKm());
        e.setNotificationEnabled(prefs.notificationEnabled());
        e.setNotificationFrequency(prefs.notificationFrequency());
        return toDomain(repo.save(e));
    }

    @Override
    public Optional<UserPreferences> findByUserId(UUID userId) {
        return repo.findByUserId(userId).map(this::toDomain);
    }

    private UserPreferences toDomain(PreferencesEntity e) {
        return new UserPreferences(e.getId(), e.getUserId(),
                toList(e.getPreferredLocations()), toList(e.getPreferredMunicipalities()),
                toList(e.getPositiveSignals()), toList(e.getNegativeSignals()),
                toList(e.getExcludedCompanies()),
                toList(e.getPreferredRemoteTypes()), toList(e.getPreferredEmploymentTypes()),
                toList(e.getPreferredSeniority()),
                e.getSalaryMin(), e.getSalaryMax(), e.getMaxCommuteKm(),
                e.isNotificationEnabled(), e.getNotificationFrequency(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static String[] toArray(List<String> l) { return l != null ? l.toArray(String[]::new) : new String[0]; }
    private static List<String> toList(String[] a) { return a != null ? Arrays.asList(a) : List.of(); }
}
