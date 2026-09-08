package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ParsedSkillSuggestionEntity;
import com.autoapplicant.adapter.persistence.repository.ParsedSkillSuggestionJpaRepository;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ParsedSkillSuggestionPersistenceAdapter implements ParsedSkillSuggestionRepositoryPort {

    private final ParsedSkillSuggestionJpaRepository repo;

    public ParsedSkillSuggestionPersistenceAdapter(ParsedSkillSuggestionJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<ParsedSkillSuggestion> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void record(ParsedSkillSuggestion s) {
        if (s.normalizedName() == null || s.normalizedName().isBlank()) return;
        // Re-importing the same CV asks the same question again; the unique index would reject it,
        // and the first row already carries the evidence the user has not yet answered.
        if (repo.existsByUserIdAndNormalizedName(s.userId(), s.normalizedName())) return;
        ParsedSkillSuggestionEntity e = new ParsedSkillSuggestionEntity();
        e.setUserId(s.userId());
        e.setSkillName(s.skillName());
        e.setNormalizedName(s.normalizedName());
        e.setEvidence(s.evidence());
        e.setSource(s.source() != null ? s.source().name() : ParsedSkillSuggestion.Source.CV_PARSE.name());
        repo.save(e);
    }

    @Override
    @Transactional
    public void remove(UUID userId, String normalizedName) {
        repo.deleteByUserIdAndNormalizedName(userId, normalizedName);
    }

    private ParsedSkillSuggestion toDomain(ParsedSkillSuggestionEntity e) {
        ParsedSkillSuggestion.Source source;
        try {
            source = ParsedSkillSuggestion.Source.valueOf(e.getSource());
        } catch (IllegalArgumentException ex) {
            source = ParsedSkillSuggestion.Source.CV_PARSE;
        }
        return new ParsedSkillSuggestion(e.getId(), e.getUserId(), e.getSkillName(),
                e.getNormalizedName(), e.getEvidence(), source);
    }
}
