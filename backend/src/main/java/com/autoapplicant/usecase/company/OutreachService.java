package com.autoapplicant.usecase.company;

import com.autoapplicant.usecase.common.Values;
import com.autoapplicant.domain.company.OutreachContact;
import com.autoapplicant.domain.company.OutreachStatus;
import com.autoapplicant.port.in.company.ManageOutreachUseCase;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Tracks unsolicited outreach: who was written to, through which channel, and when to come back.
 *
 * <p>Danish advice on unsolicited applications is consistent that the follow-up is what makes them
 * work — a letter with no follow-up is a letter into a drawer. So the follow-up date is scheduled
 * for the user rather than left as an optional field they will not fill in.
 */
@Service
public class OutreachService implements ManageOutreachUseCase {

    /**
     * Default follow-up window after making contact.
     *
     * <p>Two to three working days, which is shorter than it looks to a non-Danish eye. HK and
     * Krifa both advise ringing a couple of days after sending an unsolicited application, and the
     * expectation is that the candidate is the active party — "høfligt påtrængende". A ten-day
     * wait, which was the original guess here, is long enough for the application to have been
     * forgotten.
     */
    static final int DEFAULT_FOLLOW_UP_DAYS = 3;

    private final OutreachContactRepositoryPort repo;
    private final Clock clock;

    public OutreachService(OutreachContactRepositoryPort repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    public List<OutreachContact> list(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        // Due follow-ups first — the only thing on this list that needs action today — then the
        // rest newest first.
        return repo.findByUserId(userId).stream()
                .sorted(Comparator
                        .comparing((OutreachContact c) -> !c.isFollowUpDue(today))
                        .thenComparing(OutreachContact::createdAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public OutreachContact track(UUID userId, UUID companyId, String companyName, String contactName) {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("A tracked outreach needs a company name");
        }
        if (companyId != null) {
            var existing = repo.findByUserAndCompany(userId, companyId);
            // Saving the same target twice is the same thread of contact, not a second one.
            if (existing.isPresent()) return existing.get();
        }
        Instant now = clock.instant();
        return repo.save(new OutreachContact(null, userId, companyId, companyName.strip(),
                OutreachStatus.SAVED, null, Values.blankToNull(contactName), null, null, null, now, now));
    }

    @Override
    public OutreachContact update(UUID userId, UUID id, OutreachStatus status, String channel,
                                  LocalDate followUpDue, String notes) {
        OutreachContact existing = owned(userId, id);
        OutreachStatus newStatus = status != null ? status : existing.status();

        // The moment of contact is recorded once: a later edit must not reset the follow-up clock.
        boolean becomingContacted = newStatus == OutreachStatus.CONTACTED
                && existing.contactedAt() == null;
        Instant contactedAt = becomingContacted ? clock.instant() : existing.contactedAt();

        LocalDate resolvedFollowUp = followUpDue != null ? followUpDue
                : becomingContacted && existing.followUpDue() == null
                        ? LocalDate.now(clock).plusDays(DEFAULT_FOLLOW_UP_DAYS)
                        : existing.followUpDue();

        return repo.save(new OutreachContact(existing.id(), userId, existing.companyId(),
                existing.companyName(), newStatus,
                channel != null ? Values.blankToNull(channel) : existing.channel(),
                existing.contactName(), contactedAt, resolvedFollowUp,
                notes != null ? Values.blankToNull(notes) : existing.notes(),
                existing.createdAt(), clock.instant()));
    }

    @Override
    public void untrack(UUID userId, UUID id) {
        repo.delete(owned(userId, id).id());
    }

    /** Loads the record and refuses one that belongs to somebody else. */
    private OutreachContact owned(UUID userId, UUID id) {
        OutreachContact contact = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outreach not found"));
        if (!contact.userId().equals(userId)) {
            throw new IllegalArgumentException("Outreach not found");
        }
        return contact;
    }

}
