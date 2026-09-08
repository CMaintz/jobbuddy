package com.autoapplicant.adapter.web.dto.user;

import com.autoapplicant.domain.skill.SkillConfirmation;

import java.util.List;

/**
 * The user's answers to a round of skill suggestions.
 *
 * @param confirmations one entry per skill they answered; unanswered suggestions are simply absent
 */
public record SkillConfirmationRequest(List<SkillConfirmation> confirmations) {}
