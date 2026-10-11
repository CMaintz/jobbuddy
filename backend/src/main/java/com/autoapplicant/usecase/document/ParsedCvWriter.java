package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import com.autoapplicant.usecase.skills.ImpliedSkillQueue;
import com.autoapplicant.usecase.skills.ProfileSkillIngestion;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Files a parsed CV onto the user's profile. A separate bean from {@link ParseCvService} so the
 * transaction proxy applies and covers only these writes, never the slow AI call before them.
 */
@Service
public class ParsedCvWriter {

    private final ProfilePrivateInfoRepositoryPort privateInfoRepo;
    private final ProfileSocialRepositoryPort socialRepo;
    private final ImpliedSkillQueue impliedSkills;
    private final ProfileSkillIngestion skillIngestion;

    public ParsedCvWriter(ProfilePrivateInfoRepositoryPort privateInfoRepo,
                          ProfileSocialRepositoryPort socialRepo,
                          ImpliedSkillQueue impliedSkills,
                          ProfileSkillIngestion skillIngestion) {
        this.privateInfoRepo = privateInfoRepo;
        this.socialRepo = socialRepo;
        this.impliedSkills = impliedSkills;
        this.skillIngestion = skillIngestion;
    }

    /**
     * Writes the CV's private info, social links and skills as one unit: a failure in any of
     * them rolls back the others, so a re-import never leaves a half-imported profile.
     */
    @Transactional
    public void write(UUID userId, JsonNode node) {
        savePrivateInfo(userId, node);
        saveSocialLinks(userId, node);
        captureCvSkills(userId, node);
    }

    /** Persist the CV's name and location as private info, merging into any existing record. */
    private void savePrivateInfo(UUID userId, JsonNode node) {
        String fullName = ParsedCvFields.textOrNull(node, "fullName");
        String location = ParsedCvFields.textOrNull(node, "location");
        if (fullName == null && location == null) return;
        ProfilePrivateInfo existing = privateInfoRepo.findByUserId(userId).orElse(null);
        if (existing == null) {
            privateInfoRepo.save(new ProfilePrivateInfo(null, userId,
                    fullName, null, null, location, null, null, null, null));
        } else {
            privateInfoRepo.save(new ProfilePrivateInfo(existing.id(), userId,
                    fullName != null ? fullName : existing.fullName(),
                    existing.phone(), existing.photoUrl(),
                    location != null ? location : existing.location(),
                    existing.municipality(), existing.contactEmail(),
                    existing.createdAt(), null));
        }
    }

    /** Persist any LinkedIn / GitHub / website URLs the CV listed, after the user's existing links. */
    private void saveSocialLinks(UUID userId, JsonNode node) {
        int order = socialRepo.findByUserId(userId).size();
        saveSocialIfPresent(userId, node, "linkedinUrl", "LinkedIn", "linkedin", order);
        saveSocialIfPresent(userId, node, "githubUrl",   "GitHub",   "github",   order + 1);
        saveSocialIfPresent(userId, node, "websiteUrl",  "Website",  "globe",    order + 2);
    }

    /**
     * Capture the CV's skills, in the two ways the profile keeps them: ingest the stated ones
     * straight into the profile's skill rows (same taxonomy link and category as a hand-typed skill,
     * so grouped sections and proficiency-weighted matching work for an imported CV too), and queue
     * the inferred ones as questions - inferences never join the extracted list, so a wrong guess
     * costs one dismissed suggestion rather than a fabricated skill.
     */
    private void captureCvSkills(UUID userId, JsonNode node) {
        List<String> stated = new ArrayList<>(ParsedCvFields.arrayOrEmpty(node, "skills"));
        stated.addAll(ParsedCvFields.arrayOrEmpty(node, "technologies"));
        impliedSkills.queue(userId, node, stated, ParsedSkillSuggestion.Source.CV_PARSE);
        skillIngestion.ingest(userId, stated);
    }

    private void saveSocialIfPresent(UUID userId, JsonNode node, String field,
                                      String platform, String iconKey, int displayOrder) {
        String url = ParsedCvFields.textOrNull(node, field);
        if (url == null || url.isBlank()) return;
        List<ProfileSocial> existing = socialRepo.findByUserId(userId);
        boolean alreadyExists = existing.stream()
                .anyMatch(s -> iconKey.equalsIgnoreCase(s.iconKey()));
        if (!alreadyExists) {
            socialRepo.save(new ProfileSocial(null, userId, platform, url, null,
                    iconKey, displayOrder, null, null));
        }
    }
}
