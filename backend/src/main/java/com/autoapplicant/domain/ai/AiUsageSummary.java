package com.autoapplicant.domain.ai;

import java.util.List;

/**
 * What the user's AI generations have cost. There is no quota here on purpose —
 * the app is single-user, so the numbers are for the user's own information.
 *
 * <p>A provider that reports no token counts (the local CLI agent, which bills a
 * flat subscription) still contributes requests, so a zero token count alongside a
 * non-zero request count means "not metered", not "nothing happened".
 */
public record AiUsageSummary(
        AiUsageTotals allTime,
        AiUsageTotals today,
        List<AiOperationUsage> byOperation
) {}
