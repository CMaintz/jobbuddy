package com.autoapplicant.domain.company;

import java.time.Instant;

/** User-supplied company research (pasted by the candidate), and when it was last edited. */
public record CompanyResearch(String notes, Instant updatedAt) {}
