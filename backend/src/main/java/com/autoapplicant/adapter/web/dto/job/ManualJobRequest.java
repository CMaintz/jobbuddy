package com.autoapplicant.adapter.web.dto.job;

public record ManualJobRequest(
    String title,
    String companyName,
    String description,
    String url,
    String location,
    String remoteType,      // FULLY_REMOTE | HYBRID | ON_SITE
    String employmentType,  // FULL_TIME | PART_TIME | CONTRACT | FREELANCE
    Integer salaryMin,
    Integer salaryMax,
    String currency
) {}
