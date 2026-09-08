package com.autoapplicant.adapter.web.dto.user;

/**
 * A free-text answer to be restructured into STAR fields.
 *
 * @param answer whatever the user typed, in their own words — the whole point is that they should
 *               not have to fill in three boxes to record one thing they did
 */
public record DraftEvidenceRequest(String skillName, String answer) {}
