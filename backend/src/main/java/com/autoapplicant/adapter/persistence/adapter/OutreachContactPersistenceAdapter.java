package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.OutreachContactEntity;
import com.autoapplicant.adapter.persistence.repository.OutreachContactJpaRepository;
import com.autoapplicant.domain.company.OutreachContact;
import com.autoapplicant.domain.company.OutreachStatus;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OutreachContactPersistenceAdapter implements OutreachContactRepositoryPort {

    private final OutreachContactJpaRepository repo;

    public OutreachContactPersistenceAdapter(OutreachContactJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public OutreachContact save(OutreachContact contact) {
        OutreachContactEntity e = contact.id() != null
                ? repo.findById(contact.id()).orElseGet(OutreachContactEntity::new)
                : new OutreachContactEntity();
        e.setUserId(contact.userId());
        e.setCompanyId(contact.companyId());
        e.setCompanyName(contact.companyName());
        e.setStatus(contact.status() != null ? contact.status().name() : OutreachStatus.SAVED.name());
        e.setChannel(contact.channel());
        e.setContactName(contact.contactName());
        e.setContactedAt(contact.contactedAt());
        e.setFollowUpDue(contact.followUpDue());
        e.setNotes(contact.notes());
        return toDomain(repo.save(e));
    }

    @Override
    public Optional<OutreachContact> findById(UUID id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<OutreachContact> findByUserAndCompany(UUID userId, UUID companyId) {
        return repo.findByUserIdAndCompanyId(userId, companyId).map(this::toDomain);
    }

    @Override
    public List<OutreachContact> findByUserId(UUID userId) {
        return repo.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(UUID id) {
        repo.deleteById(id);
    }

    private OutreachContact toDomain(OutreachContactEntity e) {
        return new OutreachContact(e.getId(), e.getUserId(), e.getCompanyId(), e.getCompanyName(),
                parseStatus(e.getStatus()), e.getChannel(), e.getContactName(), e.getContactedAt(),
                e.getFollowUpDue(), e.getNotes(), e.getCreatedAt(), e.getUpdatedAt());
    }

    /** An unreadable status must not hide the record — it reads as still on the list. */
    private static OutreachStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return OutreachStatus.SAVED;
        try { return OutreachStatus.valueOf(value); }
        catch (IllegalArgumentException ex) { return OutreachStatus.SAVED; }
    }
}
