package com.autoapplicant.domain.job;


import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keyword-based job category classifier.
 *
 * An unambiguous GRC title wins first, then the source's own category, then keywords in the job
 * title, then the first 300 characters of the cleaned description. Returns
 * {@link JobCategory#OTHER} when nothing matches.
 *
 * Categories are checked in priority order so that more specific categories
 * (e.g. SOFTWARE_IT) take precedence over broader ones (e.g. MANAGEMENT).
 */

public class JobCategoryClassifier {

    private static final int DESC_PREVIEW_CHARS = 300;

    /**
     * Maps source-supplied category strings (lowercased) to our enum.
     * Covers Jobindex Danish categories and IT Jobbank categories observed in the feed.
     * Checked before keyword matching — highest confidence signal.
     */
    private static final Map<String, JobCategory> RSS_CATEGORY_MAP = new HashMap<>();

    static {
        // IT / Software
        RSS_CATEGORY_MAP.put("it-arkitekt", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("softwarearkitekt", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("infrastrukturarkitekt", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("solution architect", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("devops", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("it-drift", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("it-sikkerhed", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("it-support", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("it-projektledelse", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("programmering/softwareudvikling", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("webudvikling", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("it", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("qa / test", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("qa/test", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("embedded", JobCategory.SOFTWARE_IT);
        RSS_CATEGORY_MAP.put("cloud", JobCategory.SOFTWARE_IT);
        // Data / BI
        RSS_CATEGORY_MAP.put("business intelligence", JobCategory.DATA_ANALYTICS);
        RSS_CATEGORY_MAP.put("data warehouse / database", JobCategory.DATA_ANALYTICS);
        RSS_CATEGORY_MAP.put("data warehouse/database", JobCategory.DATA_ANALYTICS);
        RSS_CATEGORY_MAP.put("data scientist", JobCategory.DATA_ANALYTICS);
        RSS_CATEGORY_MAP.put("data analyst", JobCategory.DATA_ANALYTICS);
        // Design / UX
        RSS_CATEGORY_MAP.put("grafisk", JobCategory.DESIGN_UX);
        RSS_CATEGORY_MAP.put("grafisk design", JobCategory.DESIGN_UX);
        RSS_CATEGORY_MAP.put("ux/design", JobCategory.DESIGN_UX);
        RSS_CATEGORY_MAP.put("design", JobCategory.DESIGN_UX);
        // Marketing / Communications
        RSS_CATEGORY_MAP.put("marketing", JobCategory.MARKETING);
        RSS_CATEGORY_MAP.put("kommunikation", JobCategory.MARKETING);
        RSS_CATEGORY_MAP.put("pr", JobCategory.MARKETING);
        RSS_CATEGORY_MAP.put("journalistik/redaktion", JobCategory.MARKETING);
        // Sales
        RSS_CATEGORY_MAP.put("salg", JobCategory.SALES);
        RSS_CATEGORY_MAP.put("handel", JobCategory.SALES);
        RSS_CATEGORY_MAP.put("sales", JobCategory.SALES);
        // Finance
        RSS_CATEGORY_MAP.put("økonomi/regnskab", JobCategory.FINANCE);
        RSS_CATEGORY_MAP.put("økonomi", JobCategory.FINANCE);
        RSS_CATEGORY_MAP.put("regnskab", JobCategory.FINANCE);
        RSS_CATEGORY_MAP.put("finans", JobCategory.FINANCE);
        RSS_CATEGORY_MAP.put("revision", JobCategory.FINANCE);
        // HR
        RSS_CATEGORY_MAP.put("hr/personale", JobCategory.HR);
        RSS_CATEGORY_MAP.put("hr", JobCategory.HR);
        RSS_CATEGORY_MAP.put("personale", JobCategory.HR);
        RSS_CATEGORY_MAP.put("rekruttering", JobCategory.HR);
        // Engineering (non-software)
        RSS_CATEGORY_MAP.put("ingeniør", JobCategory.ENGINEERING);
        RSS_CATEGORY_MAP.put("teknik", JobCategory.ENGINEERING);
        RSS_CATEGORY_MAP.put("elektronik", JobCategory.ENGINEERING);
        RSS_CATEGORY_MAP.put("maskinteknik", JobCategory.ENGINEERING);
        // Operations / Logistics
        RSS_CATEGORY_MAP.put("lager", JobCategory.OPERATIONS_LOGISTICS);
        RSS_CATEGORY_MAP.put("logistik", JobCategory.OPERATIONS_LOGISTICS);
        RSS_CATEGORY_MAP.put("transport", JobCategory.OPERATIONS_LOGISTICS);
        RSS_CATEGORY_MAP.put("indkøb", JobCategory.OPERATIONS_LOGISTICS);
        RSS_CATEGORY_MAP.put("ejendomsservice", JobCategory.OPERATIONS_LOGISTICS);
        RSS_CATEGORY_MAP.put("produktion", JobCategory.OPERATIONS_LOGISTICS);
        RSS_CATEGORY_MAP.put("facility management", JobCategory.OPERATIONS_LOGISTICS);
        // Customer Service
        RSS_CATEGORY_MAP.put("kundeservice", JobCategory.CUSTOMER_SERVICE);
        RSS_CATEGORY_MAP.put("service", JobCategory.CUSTOMER_SERVICE);
        RSS_CATEGORY_MAP.put("callcenter", JobCategory.CUSTOMER_SERVICE);
        // Legal
        RSS_CATEGORY_MAP.put("jura", JobCategory.LEGAL);
        RSS_CATEGORY_MAP.put("compliance", JobCategory.GRC_COMPLIANCE);
        // Healthcare
        RSS_CATEGORY_MAP.put("sundhed", JobCategory.HEALTHCARE);
        RSS_CATEGORY_MAP.put("sygepleje", JobCategory.HEALTHCARE);
        RSS_CATEGORY_MAP.put("social/omsorg", JobCategory.HEALTHCARE);
        RSS_CATEGORY_MAP.put("pædagogik", JobCategory.EDUCATION);
        // Management
        RSS_CATEGORY_MAP.put("ledelse", JobCategory.MANAGEMENT);
        RSS_CATEGORY_MAP.put("øvrig ledelse", JobCategory.MANAGEMENT);
        RSS_CATEGORY_MAP.put("projektledelse", JobCategory.MANAGEMENT);
        RSS_CATEGORY_MAP.put("øvrig projektledelse", JobCategory.MANAGEMENT);
        RSS_CATEGORY_MAP.put("administration", JobCategory.MANAGEMENT);
        // Education
        RSS_CATEGORY_MAP.put("undervisning", JobCategory.EDUCATION);
        RSS_CATEGORY_MAP.put("forskning", JobCategory.EDUCATION);
        // Creative / Media
        RSS_CATEGORY_MAP.put("medie/kommunikation", JobCategory.CREATIVE_MEDIA);
        RSS_CATEGORY_MAP.put("foto/video", JobCategory.CREATIVE_MEDIA);
        RSS_CATEGORY_MAP.put("sikkerhed", JobCategory.OPERATIONS_LOGISTICS);
    }

    /** Priority-ordered map: first matching category wins. */
    private static final EnumMap<JobCategory, List<String>> KEYWORDS =
            new EnumMap<>(JobCategory.class);

    static {
        KEYWORDS.put(JobCategory.SOFTWARE_IT, List.of(
                "developer", "softwareudvikler", "webudvikler", "udvikler",
                "software engineer", "web engineer", "cloud engineer", "platform engineer",
                "programmer", "devops", "sre", "site reliability",
                "frontend", "front-end", "backend", "back-end", "fullstack", "full-stack",
                "mobile developer", "ios developer", "android developer",
                "qa engineer", "test engineer", "automation engineer", "tester",
                "scrum master", "tech lead", "cto", "chief technology"
        ));
        KEYWORDS.put(JobCategory.DATA_ANALYTICS, List.of(
                "data scientist", "data engineer", "data analyst", "dataanalytiker",
                "business intelligence", "bi developer", "bi analyst",
                "ml engineer", "machine learning", "analytics engineer",
                "analytics manager", "data manager"
        ));
        KEYWORDS.put(JobCategory.DESIGN_UX, List.of(
                "ux designer", "ui designer", "user experience", "user interface",
                "graphic designer", "visual designer", "product designer",
                "interaction designer", "grafiker", "designer"
        ));
        // No bare "awareness", "sikkerhed" or "beredskab": brand awareness is marketing, most
        // "sikkerhed" postings are guards, and Beredskab Fyn hires firefighters.
        KEYWORDS.put(JobCategory.GRC_COMPLIANCE, List.of(
                "informationssikkerhed", "information security officer",
                "information security manager", "information security consultant",
                "information security director", "awareness-konsulent", "awarenesskonsulent",
                "awareness-koordinator", "awarenesskoordinator", "iso/iec 27001", "grc", "governance, risk", "nis2", "nis 2",
                "nis-2", "iso 27001", "iso27001", "compliance", "gdpr", "databeskyttelse",
                "data protection", "persondata", "security awareness", "sikkerhedsawareness",
                "cyber awareness", "risikostyring", "risk management", "risk manager",
                "beredskabskoordinator", "beredskabskonsulent", "beredskabsplan",
                "kriseberedskab", "it-beredskab", "business continuity"
        ));
        KEYWORDS.put(JobCategory.MARKETING, List.of(
                "marketing", "content manager", "content strategist", "seo", "sem",
                "social media", "campaign manager", "brand manager", "communications",
                "pr manager", "public relations", "copywriter", "growth hacker",
                "marketingchef", "kommunikationschef", "kommunikation"
        ));
        KEYWORDS.put(JobCategory.SALES, List.of(
                "sales manager", "account manager", "account executive",
                "business development", "bdr", "sdr", "sales director",
                "salgschef", "sælger", "salgsrepræsentant"
        ));
        KEYWORDS.put(JobCategory.FINANCE, List.of(
                "accountant", "financial controller", "financial analyst", "finance manager",
                "cfo", "chief financial", "treasurer", "auditor", "bookkeeper",
                "payroll", "revisor", "regnskab", "økonomiansvarlig", "økonomi"
        ));
        KEYWORDS.put(JobCategory.HR, List.of(
                "hr manager", "human resources", "recruiter", "recruitment",
                "talent acquisition", "talent manager", "people operations",
                "hr business partner", "personalechef", "rekruttering"
        ));
        KEYWORDS.put(JobCategory.ENGINEERING, List.of(
                "mechanical engineer", "civil engineer", "electrical engineer",
                "structural engineer", "process engineer", "chemical engineer",
                "maskiningeniør", "civilingeniør", "konstruktionsingeniør"
        ));
        KEYWORDS.put(JobCategory.OPERATIONS_LOGISTICS, List.of(
                "operations manager", "logistics manager", "supply chain", "procurement",
                "warehouse manager", "facility manager", "logistikchef", "indkøbschef",
                "indkøb", "logistik"
        ));
        KEYWORDS.put(JobCategory.CUSTOMER_SERVICE, List.of(
                "customer service", "customer support", "helpdesk", "customer success",
                "support specialist", "kundeservicemedarbejder", "kundeservice"
        ));
        KEYWORDS.put(JobCategory.LEGAL, List.of(
                "lawyer", "legal counsel", "attorney", "general counsel",
                "jurist", "paralegal", "advokat"
        ));
        KEYWORDS.put(JobCategory.HEALTHCARE, List.of(
                "nurse", "physician", "doctor", "pharmacist", "therapist",
                "psychologist", "social worker", "sygeplejerske", "læge",
                "farmaceut", "socialrådgiver", "ergoterapeut", "fysioterapeut"
        ));
        KEYWORDS.put(JobCategory.MANAGEMENT, List.of(
                "ceo", "chief executive", "coo", "chief operating",
                "managing director", "head of", "direktør", "afdelingsleder"
        ));
        KEYWORDS.put(JobCategory.EDUCATION, List.of(
                "teacher", "lecturer", "professor", "researcher", "trainer",
                "instructor", "lærer", "forsker", "underviser", "pædagog"
        ));
        KEYWORDS.put(JobCategory.CREATIVE_MEDIA, List.of(
                "photographer", "videographer", "journalist", "animator",
                "creative director", "fotograf", "redaktør", "journalist",
                "filmproducer", "art director"
        ));
    }

    /**
     * Classifies a job. An unambiguous GRC title wins outright (see {@link GrcSignals}); then the
     * source category, when it maps to a known value; then keywords in the title, then the start
     * of the description.
     *
     * @param rawCategories  source-supplied category strings (e.g. from RSS {@code <category>} tags)
     * @param title          job title (may be null)
     * @param descriptionClean cleaned description text (may be null)
     * @return best-matching {@link JobCategory}, never null
     */
    public JobCategory classify(List<String> rawCategories, String title, String descriptionClean) {
        if (GrcSignals.isUnambiguousTitle(title == null ? "" : title.toLowerCase())) {
            return JobCategory.GRC_COMPLIANCE;
        }
        if (rawCategories != null) {
            for (String raw : rawCategories) {
                JobCategory mapped = RSS_CATEGORY_MAP.get(raw.toLowerCase().trim());
                if (mapped != null) return mapped;
            }
        }
        return classify(title, descriptionClean);
    }

    /**
     * Re-runs the rules on a job already stored. The source category was never persisted, so the
     * stored category stands in for it: only an unambiguous GRC title moves a categorised job,
     * and an uncategorised one gets the keyword classification it would get today.
     */
    public JobCategory reclassify(JobCategory stored, String title, String descriptionClean) {
        if (GrcSignals.isUnambiguousTitle(title == null ? "" : title.toLowerCase())) {
            return JobCategory.GRC_COMPLIANCE;
        }
        return stored == null || stored == JobCategory.OTHER ? classify(title, descriptionClean) : stored;
    }

    /**
     * Classifies a job by title and optional description text (keyword matching only).
     * Title is checked first; description preview is the fallback.
     *
     * @param title         job title (may be null)
     * @param descriptionClean  cleaned description text (may be null)
     * @return best-matching {@link JobCategory}, never null
     */
    public JobCategory classify(String title, String descriptionClean) {
        String lowerTitle = title != null ? title.toLowerCase() : "";
        String lowerDesc = descriptionClean != null
                ? descriptionClean.substring(0, Math.min(DESC_PREVIEW_CHARS, descriptionClean.length())).toLowerCase()
                : "";

        JobCategory byTitle = matchIn(lowerTitle, true);
        if (byTitle != null) return byTitle;
        JobCategory byDescription = matchIn(lowerDesc, false);
        return byDescription != null ? byDescription : JobCategory.OTHER;
    }

    /** The first category, in priority order, with a keyword in this lowercased text; else null. */
    private static JobCategory matchIn(String lowerText, boolean isTitle) {
        if (lowerText.isBlank()) return null;
        for (Map.Entry<JobCategory, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (!isTitle && GrcSignals.countsOnlyInTitle(keyword)) continue;
                if (KeywordMatch.contains(lowerText, keyword)) return entry.getKey();
            }
        }
        return null;
    }
}
