package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.JobCategory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Keyword-based job category classifier.
 *
 * Checks the job title first (high confidence); if no match, checks the first
 * 300 characters of the cleaned description. Returns {@link JobCategory#OTHER}
 * when nothing matches.
 *
 * Categories are checked in priority order so that more specific categories
 * (e.g. SOFTWARE_IT) take precedence over broader ones (e.g. MANAGEMENT).
 */
@Service
public class JobCategoryClassifier {

    private static final int DESC_PREVIEW_CHARS = 300;

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
                "jurist", "compliance officer", "gdpr", "paralegal", "advokat"
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
     * Classifies a job by title and optional description text.
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

        for (Map.Entry<JobCategory, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (!lowerTitle.isBlank() && lowerTitle.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        for (Map.Entry<JobCategory, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (!lowerDesc.isBlank() && lowerDesc.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        return JobCategory.OTHER;
    }
}
