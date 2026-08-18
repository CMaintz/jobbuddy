package com.autoapplicant.domain.company;

import java.time.Instant;

/** Cached AI-extracted verified facts about a company (from its own website). */
public record CompanyFacts(String facts, Instant researchedAt) {}
