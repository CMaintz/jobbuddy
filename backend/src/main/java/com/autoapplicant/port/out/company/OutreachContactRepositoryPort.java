package com.autoapplicant.port.out.company;

import com.autoapplicant.domain.company.OutreachContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutreachContactRepositoryPort {
    OutreachContact save(OutreachContact contact);
    Optional<OutreachContact> findById(UUID id);
    Optional<OutreachContact> findByUserAndCompany(UUID userId, UUID companyId);
    List<OutreachContact> findByUserId(UUID userId);
    void delete(UUID id);
}
