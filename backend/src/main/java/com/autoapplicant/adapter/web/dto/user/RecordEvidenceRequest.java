package com.autoapplicant.adapter.web.dto.user;

/**
 * Evidence for one claimed skill, in the three parts a letter can actually cite.
 *
 * @param situation where it happened — optional context
 * @param action    what the candidate did
 * @param result    what was different afterwards
 */
public record RecordEvidenceRequest(String skillName, String situation, String action, String result) {}
