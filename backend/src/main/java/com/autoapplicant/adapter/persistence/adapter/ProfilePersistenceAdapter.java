package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfileEntity;
import com.autoapplicant.adapter.persistence.repository.ProfileJpaRepository;
import com.autoapplicant.domain.job.EmploymentType;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProfilePersistenceAdapter implements ProfileRepositoryPort {

    private final ProfileJpaRepository repo;

    public ProfilePersistenceAdapter(ProfileJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Profile save(Profile profile) {
        ProfileEntity e = repo.findByUserId(profile.userId()).orElse(new ProfileEntity());
        e.setUserId(profile.userId());
        e.setFullName(profile.fullName());
        e.setHeadline(profile.headline());
        e.setSummary(profile.summary());
        e.setLocation(profile.location());
        e.setMunicipality(profile.municipality());
        e.setLinkedinUrl(profile.linkedinUrl());
        e.setGithubUrl(profile.githubUrl());
        e.setWebsiteUrl(profile.websiteUrl());
        e.setPhone(profile.phone());
        e.setPhotoUrl(profile.photoUrl());
        e.setYearsExperience(profile.yearsExperience());
        e.setSkills(toArray(profile.skills()));
        e.setTechnologies(toArray(profile.technologies()));
        e.setLanguages(toArray(profile.languages()));
        e.setDesiredSalaryMin(profile.desiredSalaryMin());
        e.setDesiredSalaryMax(profile.desiredSalaryMax());
        e.setDesiredCurrency(profile.desiredCurrency());
        e.setRemotePreference(profile.remotePreference() != null ? profile.remotePreference().name() : null);
        e.setEmploymentTypePreference(profile.employmentTypePreference() != null ? profile.employmentTypePreference().name() : null);
        return toDomain(repo.save(e));
    }

    @Override
    public Optional<Profile> findByUserId(UUID userId) {
        return repo.findByUserId(userId).map(this::toDomain);
    }

    private Profile toDomain(ProfileEntity e) {
        return new Profile(e.getId(), e.getUserId(), e.getFullName(), e.getHeadline(),
                e.getSummary(), e.getLocation(), e.getMunicipality(),
                e.getLinkedinUrl(), e.getGithubUrl(), e.getWebsiteUrl(), e.getPhone(),
                e.getPhotoUrl(), e.getYearsExperience(),
                toList(e.getSkills()), toList(e.getTechnologies()), toList(e.getLanguages()),
                e.getDesiredSalaryMin(), e.getDesiredSalaryMax(), e.getDesiredCurrency(),
                parseEnum(e.getRemotePreference(), RemoteType.class),
                parseEnum(e.getEmploymentTypePreference(), EmploymentType.class),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static <T extends Enum<T>> T parseEnum(String v, Class<T> c) {
        if (v == null) return null;
        try { return Enum.valueOf(c, v); } catch (IllegalArgumentException ex) { return null; }
    }

    private static List<String> toList(String[] arr) { return arr != null ? Arrays.asList(arr) : List.of(); }
    private static String[] toArray(List<String> l) { return l != null ? l.toArray(String[]::new) : new String[0]; }
}
