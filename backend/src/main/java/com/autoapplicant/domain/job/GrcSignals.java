package com.autoapplicant.domain.job;

import java.util.List;
import java.util.Set;

/**
 * How strong a governance, risk and compliance signal has to be before it may override what the
 * source or the rest of the posting says.
 *
 * <p>Two tiers, because the words are shared with neighbouring fields. "Kreditrisikostyring" is
 * banking, "IT-sikkerhedsspecialist, NIS2" is hands-on security and "Jurist med speciale i GDPR" is
 * law, and each of those has a correct source category that a GRC keyword must not overrule.
 */
final class GrcSignals {

    private GrcSignals() {}

    /**
     * Job titles that only exist in GRC work. These outrank the source category, which files the
     * same jobs as IT security, as guards ("sikkerhed") or as law.
     */
    private static final List<String> UNAMBIGUOUS_TITLES = List.of(
            "informationssikkerhedskoordinator", "informationssikkerhedskonsulent",
            "information security officer", "information security consultant",
            "awareness-konsulent", "awarenesskonsulent", "awareness-koordinator",
            "awarenesskoordinator", "grc", "nis2-koordinator", "nis2-projekt",
            "compliance officer", "compliance-konsulent", "compliancekonsulent",
            "compliance konsulent", "databeskyttelseskoordinator", "beredskabskoordinator");

    /**
     * GRC keywords that only count in a title. In a body they are routine: an HR ad promises to
     * "sikre compliance med GDPR" and a pharma QA ad asks for "GMP compliance".
     */
    private static final Set<String> TITLE_ONLY = Set.of(
            "compliance", "gdpr", "persondata", "data protection", "databeskyttelse",
            "risk management", "risk manager", "risikostyring");

    static boolean isUnambiguousTitle(String lowerTitle) {
        return UNAMBIGUOUS_TITLES.stream().anyMatch(t -> KeywordMatch.contains(lowerTitle, t));
    }

    static boolean countsOnlyInTitle(String keyword) {
        return TITLE_ONLY.contains(keyword);
    }
}
