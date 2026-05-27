package com.autoapplicant.port.in.skills;

import java.util.List;
import java.util.UUID;

public interface GetSkillGapUseCase {

    record SkillGapResult(List<String> matched, List<String> missing, int coveragePct) {}

    SkillGapResult analyzeSkillGap(UUID jobId, UUID userId);
}
