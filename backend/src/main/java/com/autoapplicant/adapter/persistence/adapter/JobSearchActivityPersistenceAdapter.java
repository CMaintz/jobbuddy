package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.repository.ApplicationStatusEventJpaRepository;
import com.autoapplicant.adapter.persistence.repository.GeneratedDocumentJpaRepository;
import com.autoapplicant.adapter.persistence.repository.OutreachContactJpaRepository;
import com.autoapplicant.port.out.analytics.JobSearchActivityPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JobSearchActivityPersistenceAdapter implements JobSearchActivityPort {

    private final ApplicationStatusEventJpaRepository statusEvents;
    private final GeneratedDocumentJpaRepository documents;
    private final OutreachContactJpaRepository outreach;

    public JobSearchActivityPersistenceAdapter(ApplicationStatusEventJpaRepository statusEvents,
                                               GeneratedDocumentJpaRepository documents,
                                               OutreachContactJpaRepository outreach) {
        this.statusEvents = statusEvents;
        this.documents = documents;
        this.outreach = outreach;
    }

    @Override
    public List<Instant> findActivitySince(UUID userId, Instant since) {
        List<Instant> activity = new ArrayList<>(statusEvents.findOccurredAtSince(userId, since));
        activity.addAll(documents.findCreatedAtSince(userId, since));
        activity.addAll(outreach.findCreatedAtSince(userId, since));
        activity.addAll(outreach.findContactedAtSince(userId, since));
        return activity;
    }
}
