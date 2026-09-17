package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.port.in.skills.GetSkillGapUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service("jobSkillGapService")
public class JobSkillGapService implements GetSkillGapUseCase {

    private final JobRepositoryPort jobs;
    private final ProfileSkillRepositoryPort profileSkills;
    private final SkillCanonicalizer skillCanonicalizer;

    public JobSkillGapService(JobRepositoryPort jobs, ProfileSkillRepositoryPort profileSkills,
                           SkillCanonicalizer skillCanonicalizer) {
        this.jobs = jobs;
        this.profileSkills = profileSkills;
        this.skillCanonicalizer = skillCanonicalizer;
    }

    @Override
    public SkillGapResult analyzeSkillGap(UUID jobId, UUID userId) {
        Job job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        List<String> requirements = requiredSkills(job);
        if (requirements.isEmpty()) {
            return new SkillGapResult(List.of(), List.of(), 100);
        }
        return coverageOf(requirements, userId);
    }

    /** The posting's demands — skills and technologies merged and de-duplicated, in posting wording. */
    private static List<String> requiredSkills(Job job) {
        return Stream.concat(
                job.skills() != null ? job.skills().stream() : Stream.empty(),
                job.technologies() != null ? job.technologies().stream() : Stream.empty()
        ).distinct().toList();
    }

    /** Partitions the requirements into matched/missing against the user's held skills, with a coverage %. */
    private SkillGapResult coverageOf(List<String> requirements, UUID userId) {
        // User's skills, canonicalised so "Kubernetes" answers a requirement written "k8s". The
        // requirement side is canonicalised the same way before comparison.
        Set<String> heldCanonical = profileSkills.findByUserId(userId).stream()
                .map(ProfileSkill::skillName)
                .map(skillCanonicalizer::canonical)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String req : requirements) {
            if (heldCanonical.contains(skillCanonicalizer.canonical(req))) {
                matched.add(req);
            } else {
                missing.add(req);
            }
        }

        int coveragePct = (int) Math.round((double) matched.size() / requirements.size() * 100);
        return new SkillGapResult(matched, missing, coveragePct);
    }
}
