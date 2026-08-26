package com.autoapplicant.adapter.web.dto.user;

import com.autoapplicant.domain.user.CareerStage;
import com.autoapplicant.domain.user.CareerTarget;

import java.time.LocalDate;
import java.util.List;

public record CareerTargetRequest(
        List<String> targetArchetypes,
        String northStar,
        String narrative,
        List<String> cultureRequirements,
        CareerStage careerStage,
        /** Free text: "3 måneder", "1 month", "negotiable". */
        String noticePeriod,
        LocalDate earliestStartDate
) {
    /** The draft to hand the use case; user id and timestamp are the server's to set. */
    public CareerTarget toDraft() {
        return new CareerTarget(null, targetArchetypes, northStar, narrative, cultureRequirements,
                careerStage, noticePeriod, earliestStartDate, null);
    }
}
