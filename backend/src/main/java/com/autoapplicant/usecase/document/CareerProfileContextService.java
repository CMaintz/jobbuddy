package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.user.InterviewStoryRepositoryPort;
import com.autoapplicant.port.out.user.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CareerProfileContextService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final ProfileRepositoryPort profileRepo;
    private final WorkExperienceRepositoryPort workExpRepo;
    private final ProjectRepositoryPort projectRepo;
    private final EducationRepositoryPort educationRepo;
    private final CertificationRepositoryPort certRepo;
    private final ProfileSkillRepositoryPort skillRepo;
    private final SkillTaxonomyRepositoryPort taxonomyRepo;
    private final SpokenLanguageRepositoryPort languageRepo;
    private final ProfileStrengthRepositoryPort strengthRepo;
    private final CareerTargetRepositoryPort careerTargetRepo;
    private final InterviewStoryRepositoryPort storyRepo;
    private final ObjectMapper objectMapper;

    public CareerProfileContextService(ProfileRepositoryPort profileRepo,
                                       WorkExperienceRepositoryPort workExpRepo,
                                       ProjectRepositoryPort projectRepo,
                                       EducationRepositoryPort educationRepo,
                                       CertificationRepositoryPort certRepo,
                                       ProfileSkillRepositoryPort skillRepo,
                                       SkillTaxonomyRepositoryPort taxonomyRepo,
                                       SpokenLanguageRepositoryPort languageRepo,
                                       ProfileStrengthRepositoryPort strengthRepo,
                                       CareerTargetRepositoryPort careerTargetRepo,
                                       InterviewStoryRepositoryPort storyRepo,
                                       ObjectMapper objectMapper) {
        this.profileRepo = profileRepo;
        this.workExpRepo = workExpRepo;
        this.projectRepo = projectRepo;
        this.educationRepo = educationRepo;
        this.certRepo = certRepo;
        this.skillRepo = skillRepo;
        this.taxonomyRepo = taxonomyRepo;
        this.languageRepo = languageRepo;
        this.strengthRepo = strengthRepo;
        this.careerTargetRepo = careerTargetRepo;
        this.storyRepo = storyRepo;
        this.objectMapper = objectMapper;
    }

    public CareerProfileForAi build(UUID userId) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        List<ProfileSkill> profileSkills = skillRepo.findByUserId(userId);
        List<String> skillNames = profileSkills.stream()
                .map(ProfileSkill::skillName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<String> skills = !skillNames.isEmpty()
                ? skillNames
                : listOrEmpty(profile != null ? profile.skills() : null);

        // Skill → category, so the tailored CV can group skills.
        //
        // Two sources, in this order. The user's own structured skills win: a category they filed
        // deliberately is the best answer there is. The taxonomy then covers everything else.
        //
        // That second pass is what makes this work at all for most people. Only manual entry and
        // confirmed suggestions create profile_skills rows — a CV upload or LinkedIn import writes
        // the flat profile.skills/technologies arrays and no rows whatsoever. So for anyone who
        // onboarded the normal way, categorising only the structured rows categorised nothing, and
        // every CV came out as one flat wall of skills.
        Map<String, String> skillCategories = new LinkedHashMap<>();
        profileSkills.stream()
                .filter(s -> s.skillName() != null && s.category() != null && !s.category().isBlank())
                .forEach(s -> skillCategories.put(s.skillName(), s.category()));

        List<String> uncategorised = java.util.stream.Stream
                .concat(skills.stream(), listOrEmpty(profile != null ? profile.technologies() : null).stream())
                .filter(Objects::nonNull)
                .filter(name -> !skillCategories.containsKey(name))
                .distinct()
                .toList();
        if (!uncategorised.isEmpty()) {
            Map<String, String> byNormalizedName = taxonomyRepo
                    .findByNormalizedNames(uncategorised.stream()
                            .map(n -> n.strip().toLowerCase(java.util.Locale.ROOT))
                            .collect(java.util.stream.Collectors.toSet()))
                    .stream()
                    .filter(t -> t.category() != null && !t.category().isBlank())
                    .collect(java.util.stream.Collectors.toMap(
                            SkillTaxonomy::normalizedName, SkillTaxonomy::category, (a, b) -> a));
            uncategorised.forEach(name -> {
                String category = byNormalizedName.get(name.strip().toLowerCase(java.util.Locale.ROOT));
                if (category != null) skillCategories.put(name, category);
            });
        }

        List<String> spokenLanguages = languageRepo.findByUserId(userId).stream()
                .map(lang -> lang.language() + " (" + formatProficiency(lang.proficiency()) + ")")
                .toList();

        List<String> strengths = strengthRepo.findByUserId(userId).stream()
                .filter(s -> s.title() != null && !s.title().isBlank())
                .map(s -> s.description() != null && !s.description().isBlank()
                        ? s.title() + ": " + s.description()
                        : s.title())
                .toList();

        CareerTarget target = careerTargetRepo.findByUserId(userId).orElse(null);

        return new CareerProfileForAi(
                profile != null ? profile.headline() : null,
                profile != null ? profile.summary() : null,
                skills,
                listOrEmpty(profile != null ? profile.technologies() : null),
                listOrEmpty(profile != null ? profile.languages() : null),
                spokenLanguages,
                listOrEmpty(profile != null ? profile.interests() : null),
                workExpRepo.findByUserId(userId).stream().map(this::toItem).toList(),
                projectRepo.findByUserId(userId).stream().map(this::toItem).toList(),
                educationRepo.findByUserId(userId).stream().map(this::toItem).toList(),
                certRepo.findByUserId(userId).stream().map(this::toItem).toList(),
                strengths,
                proofPoints(userId),
                target != null ? listOrEmpty(target.targetArchetypes()) : List.of(),
                target != null ? target.northStar() : null,
                target != null ? target.narrative() : null,
                target != null && target.careerStage() != null ? target.careerStage().name() : null,
                skillCategories,
                formatAvailability(target)
        );
    }

    /**
     * The story bank rendered as proof points: "Kubernetes — moved 30 services onto it. Deploys
     * went from 40 minutes to 9."
     *
     * <p>This is the payoff of evidence elicitation. Until the stories reach the prompt, recording
     * them changes nothing about what gets written — and the evaluator's evidence dimension only
     * counts terms that are in the profile, so an uncited story cannot even be measured.
     */
    private List<String> proofPoints(UUID userId) {
        return storyRepo.findByUserId(userId).stream()
                .map(CareerProfileContextService::renderStory)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String renderStory(com.autoapplicant.domain.user.InterviewStory story) {
        List<String> parts = new ArrayList<>();
        if (story.situation() != null && !story.situation().isBlank()) parts.add(story.situation().strip());
        if (story.action() != null && !story.action().isBlank()) parts.add(story.action().strip());
        if (story.result() != null && !story.result().isBlank()) parts.add(story.result().strip());
        if (parts.isEmpty()) return null;
        String label = story.title() != null && !story.title().isBlank() ? story.title().strip() : null;
        return label != null ? label + " — " + String.join(". ", parts) : String.join(". ", parts);
    }

    /**
     * Notice period and earliest start date as one line for the prompt, or null when the user
     * stated neither — an absent line is the signal to the model that it must not claim any
     * availability at all.
     */
    private static String formatAvailability(CareerTarget target) {
        if (target == null) return null;
        List<String> parts = new ArrayList<>();
        if (target.noticePeriod() != null && !target.noticePeriod().isBlank()) {
            parts.add("Notice period: " + target.noticePeriod().strip());
        }
        if (target.earliestStartDate() != null) {
            parts.add("available from " + target.earliestStartDate().format(DATE_FORMAT));
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static String formatProficiency(LanguageProficiency p) {
        if (p == null) return "";
        return switch (p) {
            case NATIVE -> "Native";
            case FLUENT -> "Fluent";
            case PROFESSIONAL -> "Professional working proficiency";
            case CONVERSATIONAL -> "Conversational";
            case ELEMENTARY -> "Elementary";
        };
    }

    public String buildJson(UUID userId) {
        try {
            return objectMapper.writeValueAsString(build(userId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize contact-free career profile", e);
        }
    }

    private StructuredDocumentItem toItem(WorkExperience exp) {
        return new StructuredDocumentItem(
                id(exp.id()),
                exp.title(),
                exp.companyName(),
                exp.location(),
                dateRange(exp.startDate(), exp.endDate(), exp.isCurrent()),
                exp.description(),
                listOrEmpty(exp.achievements()),
                listOrEmpty(exp.technologies()),
                List.of(),
                toSkillNames(exp.skills()),
                null);
    }

    private StructuredDocumentItem toItem(Project project) {
        List<String> bullets = new ArrayList<>();
        addIfPresent(bullets, project.measurableOutcomes());
        addIfPresent(bullets, project.businessImpact());
        addIfPresent(bullets, project.architectureNotes());
        List<String> links = new ArrayList<>();
        addIfPresent(links, project.githubUrl());
        addIfPresent(links, project.liveUrl());
        return new StructuredDocumentItem(
                id(project.id()),
                project.name(),
                null,
                null,
                dateRange(project.startDate(), project.endDate(), false),
                project.description(),
                bullets,
                listOrEmpty(project.technologies()),
                links,
                toSkillNames(project.skills()),
                null);
    }

    private StructuredDocumentItem toItem(Education education) {
        return new StructuredDocumentItem(
                id(education.id()),
                education.degree(),
                education.institution(),
                null,
                dateRange(education.startDate(), education.endDate(), false),
                education.description(),
                education.grade() != null && !education.grade().isBlank() ? List.of(education.grade()) : List.of(),
                List.of(),
                List.of(),
                toSkillNames(education.skills()),
                null);
    }

    private StructuredDocumentItem toItem(Certification certification) {
        List<String> links = certification.credentialUrl() != null && !certification.credentialUrl().isBlank()
                ? List.of(certification.credentialUrl()) : List.of();
        return new StructuredDocumentItem(
                id(certification.id()),
                certification.name(),
                certification.issuer(),
                null,
                dateRange(certification.issuedAt(), certification.expiresAt(), false),
                null,
                List.of(),
                List.of(),
                links,
                List.of(),
                null);
    }

    private String dateRange(LocalDate start, LocalDate end, boolean current) {
        if (start == null && end == null && !current) return null;
        String from = start != null ? MONTH_FORMAT.format(start) : "";
        String to = current ? "Present" : (end != null ? MONTH_FORMAT.format(end) : "");
        if (from.isBlank()) return to;
        if (to.isBlank()) return from;
        return from + " - " + to;
    }

    private static String id(UUID id) {
        return id != null ? id.toString() : null;
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private static List<String> listOrEmpty(List<String> values) {
        return values != null ? values : List.of();
    }

    private static List<String> toSkillNames(List<SkillTaxonomy> skills) {
        if (skills == null || skills.isEmpty()) return List.of();
        return skills.stream().map(SkillTaxonomy::name).filter(Objects::nonNull).toList();
    }
}
