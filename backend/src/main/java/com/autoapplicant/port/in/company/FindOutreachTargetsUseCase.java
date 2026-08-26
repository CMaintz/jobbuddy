package com.autoapplicant.port.in.company;

import com.autoapplicant.domain.company.OutreachTarget;

import java.util.List;
import java.util.UUID;

public interface FindOutreachTargetsUseCase {

    /**
     * Companies worth an unsolicited application, best first.
     *
     * @param includeCompaniesHiringNow when true, companies with a matching role open right now are
     *                                  included (ranked lower) instead of being left out — they are
     *                                  a worse unsolicited target because the right move there is
     *                                  to apply to the posting
     */
    List<OutreachTarget> findOutreachTargets(UUID userId, int limit, boolean includeCompaniesHiringNow);
}
