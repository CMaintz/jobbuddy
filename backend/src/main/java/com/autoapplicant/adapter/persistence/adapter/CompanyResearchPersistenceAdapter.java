package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.UserCompanyNoteEntity;
import com.autoapplicant.adapter.persistence.repository.UserCompanyNoteJpaRepository;
import com.autoapplicant.domain.company.CompanyResearch;
import com.autoapplicant.port.out.company.CompanyResearchRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CompanyResearchPersistenceAdapter implements CompanyResearchRepositoryPort {

    private final UserCompanyNoteJpaRepository repo;

    public CompanyResearchPersistenceAdapter(UserCompanyNoteJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<CompanyResearch> findResearch(UUID userId, UUID companyId) {
        return repo.findById(new UserCompanyNoteEntity.Key(userId, companyId))
                .map(e -> new CompanyResearch(e.getNotes(), e.getUpdatedAt()));
    }

    @Override
    @Transactional
    public void saveResearch(UUID userId, UUID companyId, String notes) {
        UserCompanyNoteEntity.Key key = new UserCompanyNoteEntity.Key(userId, companyId);
        if (notes == null || notes.isBlank()) {
            repo.deleteById(key);
            return;
        }
        UserCompanyNoteEntity entity = repo.findById(key).orElseGet(() -> new UserCompanyNoteEntity(key));
        entity.setNotes(notes);
        repo.saveAndFlush(entity);
    }
}
