package com.autoapplicant.domain.job;

/**
 * The feed's industry buckets. Declaration order is the classifier's keyword priority: the first
 * category whose keyword a title contains wins, so GRC_COMPLIANCE sits ahead of MARKETING (a
 * "kommunikationsmedarbejder til security awareness" is a GRC hire) and behind SOFTWARE_IT (a
 * developer building a GDPR feature is still a developer). Stored by name, never by ordinal.
 */
public enum JobCategory {
    SOFTWARE_IT, DATA_ANALYTICS, DESIGN_UX, GRC_COMPLIANCE, MARKETING, SALES,
    FINANCE, HR, ENGINEERING, OPERATIONS_LOGISTICS, CUSTOMER_SERVICE,
    LEGAL, HEALTHCARE, MANAGEMENT, EDUCATION, CREATIVE_MEDIA, OTHER
}
